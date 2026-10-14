//! Lokálna (nezdieľaná) session Rust appky – náhrada `HttpSession` Java verzie, drží kritériá vyhľadávania
//! (`@SessionAttributes`). Vlastná cookie s cestou /crm-rs, aby sa za jedným nginx nebila s Java ani Liferay.

use std::any::Any;
use std::collections::HashMap;
use std::sync::{Mutex, MutexGuard, PoisonError};
use std::time::{Duration, Instant};

use uuid::Uuid;

use crate::CONTEXT_PATH;

/// Cookie lokálnej session (Java: `CRM_SESSION`, Path=/crm).
pub const COOKIE: &str = "CRM_RS_SESSION";
/// Ako predvolený timeout HttpSession (30 min nečinnosti).
const IDLE_TIMEOUT: Duration = Duration::from_secs(30 * 60);

type Attributes = HashMap<&'static str, Box<dyn Any + Send + Sync>>;

struct Entry {
    attributes: Attributes,
    last_access: Instant,
}

/// Lokálne session v pamäti procesu (jedna inštancia appky – ako HttpSession bez replikácie v Jave).
#[derive(Default)]
pub struct LocalSessions {
    entries: Mutex<HashMap<String, Entry>>,
}

impl LocalSessions {
    /// Hodnota kľúča v session `id`; neznáma/expirovaná session alebo iný typ = prázdno.
    pub fn get<T: Clone + 'static>(&self, id: Option<&str>, key: &'static str) -> Option<T> {
        let mut entries = self.lock();
        let entry = entries.get_mut(id?)?;
        if entry.last_access.elapsed() >= IDLE_TIMEOUT {
            return None;
        }
        entry.last_access = Instant::now();
        entry.attributes.get(key)?.downcast_ref::<T>().cloned()
    }

    /// Uloží hodnotu; ak session ešte nie je, založí ju a vráti jej ID pre Set-Cookie (Java `getSession(true)`).
    pub fn put<T: Send + Sync + 'static>(&self, id: Option<&str>, key: &'static str, value: T) -> Option<String> {
        let mut entries = self.lock();
        entries.retain(|_, entry| entry.last_access.elapsed() < IDLE_TIMEOUT);
        if let Some(entry) = id.and_then(|id| entries.get_mut(id)) {
            entry.attributes.insert(key, Box::new(value));
            entry.last_access = Instant::now();
            return None;
        }
        let new_id = Uuid::new_v4().to_string();
        let mut attributes = Attributes::new();
        attributes.insert(key, Box::new(value));
        entries.insert(new_id.clone(), Entry { attributes, last_access: Instant::now() });
        Some(new_id)
    }

    /// Odstráni kľúč (Java `SessionStatus.setComplete()` pre `@SessionAttributes`).
    pub fn remove(&self, id: Option<&str>, key: &'static str) {
        let Some(id) = id else { return };
        if let Some(entry) = self.lock().get_mut(id) {
            entry.attributes.remove(key);
        }
    }

    fn lock(&self) -> MutexGuard<'_, HashMap<String, Entry>> {
        self.entries.lock().unwrap_or_else(PoisonError::into_inner)
    }
}

/// Hodnota hlavičky Set-Cookie pre novú session.
pub fn set_cookie_value(id: &str) -> String {
    format!("{COOKIE}={id}; Path={CONTEXT_PATH}; HttpOnly; SameSite=Lax")
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Prvý zápis založí session a vráti ID pre cookie, ďalší zápis do nej nové ID nevracia.
    #[test]
    fn should_create_session_once_when_value_put() {
        let sessions = LocalSessions::default();
        let id = sessions.put(None, "k", 1_u32).expect("nové ID");
        assert_eq!(sessions.put(Some(&id), "k", 2_u32), None);
        assert_eq!(sessions.get::<u32>(Some(&id), "k"), Some(2));
    }

    /// Neznáme ID (podvrhnutá alebo stará cookie) nedá cudzie dáta a založí novú session.
    #[test]
    fn should_isolate_sessions_when_ids_differ() {
        let sessions = LocalSessions::default();
        let id = sessions.put(None, "k", "a".to_string()).expect("ID");
        assert_eq!(sessions.get::<String>(Some("iné-id"), "k"), None);
        assert_eq!(sessions.get::<String>(None, "k"), None);
        assert!(sessions.put(Some("iné-id"), "k", "b".to_string()).is_some_and(|new| new != id));
    }

    /// Odstránenie kľúča a iný typ pri čítaní vrátia prázdno.
    #[test]
    fn should_return_none_when_removed_or_type_differs() {
        let sessions = LocalSessions::default();
        let id = sessions.put(None, "k", 1_u32).expect("ID");
        assert_eq!(sessions.get::<String>(Some(&id), "k"), None);
        sessions.remove(Some(&id), "k");
        assert_eq!(sessions.get::<u32>(Some(&id), "k"), None);
    }

    /// Cookie má cestu /crm-rs – nesmie prepísať CRM_SESSION (Java, /crm) ani JSESSIONID (Liferay).
    #[test]
    fn should_scope_cookie_to_context_path_when_set() {
        assert_eq!(set_cookie_value("abc"), "CRM_RS_SESSION=abc; Path=/crm-rs; HttpOnly; SameSite=Lax");
    }
}
