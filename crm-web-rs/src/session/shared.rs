//! Session fasáda pre jeden request – kontrakt legacy `SessionManagerCustom`, nová implementácia. Identitu
//! vlastní SSO (je v `Ctx`, fasáda ju nemení); zdieľané hodnoty sú v Redise, výpadok request nezhodí.

use std::sync::Arc;

use crate::session::keys::{MESSAGES, SharedValue, UiMessage, UiMessages};
use crate::session::store::SharedSessionStore;

/// Zdieľaná session operátora (`None` SSO ID = neprihlásený, nič sa nečíta ani nezapisuje).
#[derive(Clone)]
pub struct CrmSession {
    sso_id: Option<String>,
    store: Arc<dyn SharedSessionStore>,
}

impl CrmSession {
    /// Fasáda nad úložiskom pre danú SSO session.
    pub fn new(sso_id: Option<String>, store: Arc<dyn SharedSessionStore>) -> Self {
        Self { sso_id, store }
    }

    /// Zdieľaná hodnota; chyba úložiska alebo neplatný JSON = prázdno + log.
    pub async fn get<T: SharedValue>(&self) -> Option<T> {
        let sso_id = self.sso_id.as_deref()?;
        match self.store.get_json(sso_id, T::KEY).await {
            Ok(json) => json.and_then(|json| {
                serde_json::from_str(&json).map_err(|e| tracing::error!("Neplatný JSON v {}: {e}", T::KEY)).ok()
            }),
            Err(e) => {
                tracing::error!("Čítanie {} zo zdieľanej session zlyhalo: {e}", T::KEY);
                None
            }
        }
    }

    /// Zapíše zdieľanú hodnotu; chyba sa zaloguje.
    pub async fn set<T: SharedValue>(&self, value: &T) {
        let Some(sso_id) = self.sso_id.as_deref() else { return };
        let json = match serde_json::to_string(value) {
            Ok(json) => json,
            Err(e) => return tracing::error!("JSON pre {} sa nedá vytvoriť: {e}", T::KEY),
        };
        if let Err(e) = self.store.put_json(sso_id, T::KEY, json).await {
            tracing::error!("Zápis {} do zdieľanej session zlyhal: {e}", T::KEY);
        }
    }

    /// Pridá informačnú hlášku (zobrazí sa raz v ktorejkoľvek appke).
    pub async fn push_info(&self, key: &str) {
        self.push(UiMessage::info(key)).await;
    }

    /// Pridá chybovú hlášku.
    pub async fn push_error(&self, key: &str) {
        self.push(UiMessage::error(key)).await;
    }

    async fn push(&self, message: UiMessage) {
        let mut messages = self.get::<UiMessages>().await.unwrap_or_default();
        messages.0.push(message);
        // Vždy celý nový zoznam – rovnako ako Java aj stará appka (žiadna úprava „na mieste“).
        self.set(&messages).await;
    }

    /// Vráti a odstráni čakajúce hlášky – zobrazia sa práve raz (aj cez redirect, aj z inej appky).
    pub async fn pop_messages(&self) -> Vec<UiMessage> {
        let messages = self.get::<UiMessages>().await.unwrap_or_default().0;
        if let (false, Some(sso_id)) = (messages.is_empty(), self.sso_id.as_deref()) {
            if let Err(e) = self.store.remove(sso_id, MESSAGES).await {
                tracing::error!("Odstránenie hlášok zo zdieľanej session zlyhalo: {e}");
            }
        }
        messages
    }

    /// Flag starej appky pre ladiaci riadok; pri výpadku prázdno.
    pub async fn legacy_read_source(&self) -> Option<String> {
        match self.store.legacy_read_source().await {
            Ok(value) => value,
            Err(e) => {
                tracing::warn!("Flag starej appky sa nedá prečítať: {e}");
                None
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::session::keys::{LastViewedRecord, MESSAGES};
    use crate::session::store::InMemorySharedSessionStore;

    fn session(store: &Arc<InMemorySharedSessionStore>, sso_id: Option<&str>) -> CrmSession {
        CrmSession::new(sso_id.map(String::from), store.clone())
    }

    /// Zdieľaný kľúč sa zapíše ako JSON, ktorý číta Java appka aj Liferay.
    #[tokio::test]
    async fn should_write_legacy_json_when_shared_value_set() {
        let store = Arc::new(InMemorySharedSessionStore::default());
        session(&store, Some("sso-1")).set(&LastViewedRecord { id: 1, full_name: "Peter Vzorka".into() }).await;
        assert_eq!(
            store.field("sso-1", "APP_LAST_VIEWED_RECORD").as_deref(),
            Some(r#"{"id":1,"fullName":"Peter Vzorka"}"#)
        );
        let read: Option<LastViewedRecord> = session(&store, Some("sso-1")).get().await;
        assert_eq!(read.map(|r| r.id), Some(1));
    }

    /// Hlášky sa pridávajú ako celý nový zoznam a vyberú sa práve raz (aj keď časť zapísala iná appka).
    #[tokio::test]
    async fn should_pop_messages_once_when_read() {
        let store = Arc::new(InMemorySharedSessionStore::default());
        store.put_field("sso-1", MESSAGES, r#"[{"level":"INFO","key":"z.legacy"}]"#);
        let crm = session(&store, Some("sso-1"));
        crm.push_error("b").await;
        let keys: Vec<String> = crm.pop_messages().await.into_iter().map(|m| m.key).collect();
        assert_eq!(keys, ["z.legacy", "b"]);
        assert!(crm.pop_messages().await.is_empty());
        assert_eq!(store.field("sso-1", MESSAGES), None);
    }

    /// Bez SSO identity sa do zdieľanej session nič nezapisuje ani nečíta.
    #[tokio::test]
    async fn should_skip_store_when_not_logged_in() {
        let store = Arc::new(InMemorySharedSessionStore::default());
        let crm = session(&store, None);
        crm.push_info("a").await;
        assert!(crm.pop_messages().await.is_empty());
        assert_eq!(crm.get::<LastViewedRecord>().await, None);
    }

    /// Výpadok Redisu request nezhodí: zápis sa zaloguje, čítanie je prázdne, flag chýba.
    #[tokio::test]
    async fn should_keep_working_when_store_fails() {
        let store = Arc::new(InMemorySharedSessionStore::default());
        store.set_failing(true);
        let crm = session(&store, Some("sso-1"));
        crm.push_info("a").await;
        assert!(crm.pop_messages().await.is_empty());
        assert_eq!(crm.legacy_read_source().await, None);
    }

    /// Flag zdroja čítania starej appky sa len zobrazuje.
    #[tokio::test]
    async fn should_read_legacy_flag_when_present() {
        let store = Arc::new(InMemorySharedSessionStore::default());
        store.set_legacy_flag(Some("REDIS"));
        assert_eq!(session(&store, Some("x")).legacy_read_source().await.as_deref(), Some("REDIS"));
    }
}
