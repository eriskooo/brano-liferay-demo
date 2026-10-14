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
/// Expirované session sa upratujú najviac raz za minútu (Tomcat: `backgroundProcessorDelay`). Upratovanie pri každom
/// zápise bolo O(n) pod zámkom – klienti bez cookie (boti, health-checky) appku postupne spomalili (docs/BENCHMARK.md).
const SWEEP_INTERVAL: Duration = Duration::from_secs(60);

type Attributes = HashMap<&'static str, Box<dyn Any + Send + Sync>>;

struct Entry {
    attributes: Attributes,
    last_access: Instant,
}

struct Entries {
    by_id: HashMap<String, Entry>,
    last_sweep: Instant,
}

/// Lokálne session v pamäti procesu (jedna inštancia appky – ako HttpSession bez replikácie v Jave).
pub struct LocalSessions {
    entries: Mutex<Entries>,
    idle_timeout: Duration,
    sweep_interval: Duration,
}

impl Default for LocalSessions {
    fn default() -> Self {
        Self::with_timeouts(IDLE_TIMEOUT, SWEEP_INTERVAL)
    }
}

impl LocalSessions {
    fn with_timeouts(idle_timeout: Duration, sweep_interval: Duration) -> Self {
        let entries = Entries { by_id: HashMap::new(), last_sweep: Instant::now() };
        Self { entries: Mutex::new(entries), idle_timeout, sweep_interval }
    }

    /// Hodnota kľúča v session `id`; neznáma/expirovaná session alebo iný typ = prázdno.
    pub fn get<T: Clone + 'static>(&self, id: Option<&str>, key: &'static str) -> Option<T> {
        let mut entries = self.lock();
        let entry = entries.by_id.get_mut(id?)?;
        if entry.last_access.elapsed() >= self.idle_timeout {
            return None;
        }
        entry.last_access = Instant::now();
        entry.attributes.get(key)?.downcast_ref::<T>().cloned()
    }

    /// Uloží hodnotu; ak session ešte nie je (alebo expirovala), založí ju a vráti jej ID pre Set-Cookie
    /// (Java `getSession(true)`).
    pub fn put<T: Send + Sync + 'static>(&self, id: Option<&str>, key: &'static str, value: T) -> Option<String> {
        let mut entries = self.lock();
        self.sweep_if_due(&mut entries);
        let live = id
            .and_then(|id| entries.by_id.get_mut(id))
            .filter(|entry| entry.last_access.elapsed() < self.idle_timeout);
        if let Some(entry) = live {
            entry.attributes.insert(key, Box::new(value));
            entry.last_access = Instant::now();
            return None;
        }
        let new_id = Uuid::new_v4().to_string();
        let mut attributes = Attributes::new();
        attributes.insert(key, Box::new(value));
        entries.by_id.insert(new_id.clone(), Entry { attributes, last_access: Instant::now() });
        Some(new_id)
    }

    /// Odstráni kľúč (Java `SessionStatus.setComplete()` pre `@SessionAttributes`).
    pub fn remove(&self, id: Option<&str>, key: &'static str) {
        let Some(id) = id else { return };
        if let Some(entry) = self.lock().by_id.get_mut(id) {
            entry.attributes.remove(key);
        }
    }

    fn sweep_if_due(&self, entries: &mut Entries) {
        if entries.last_sweep.elapsed() < self.sweep_interval {
            return;
        }
        entries.by_id.retain(|_, entry| entry.last_access.elapsed() < self.idle_timeout);
        entries.last_sweep = Instant::now();
    }

    #[cfg(test)]
    fn len(&self) -> usize {
        self.lock().by_id.len()
    }

    fn lock(&self) -> MutexGuard<'_, Entries> {
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

    /// Po uplynutí intervalu upratovania zápis odstráni expirované session.
    #[test]
    fn should_remove_expired_sessions_when_sweep_interval_elapsed() {
        let sessions = LocalSessions::with_timeouts(Duration::ZERO, Duration::ZERO);
        sessions.put(None, "k", 1_u32);
        sessions.put(None, "k", 2_u32);
        assert_eq!(sessions.len(), 1);
    }

    /// Pred uplynutím intervalu sa mapa neprechádza, expirovaná session však nevráti dáta ani sa nepoužije znova.
    #[test]
    fn should_skip_sweep_but_ignore_expired_session_when_interval_not_elapsed() {
        let sessions = LocalSessions::with_timeouts(Duration::ZERO, Duration::from_secs(3600));
        let id = sessions.put(None, "k", 1_u32).expect("ID");
        assert_eq!(sessions.get::<u32>(Some(&id), "k"), None);
        assert!(sessions.put(Some(&id), "k", 2_u32).is_some_and(|new| new != id));
        assert_eq!(sessions.len(), 2);
    }

    /// Cookie má cestu /crm-rs – nesmie prepísať CRM_SESSION (Java, /crm) ani JSESSIONID (Liferay).
    #[test]
    fn should_scope_cookie_to_context_path_when_set() {
        assert_eq!(set_cookie_value("abc"), "CRM_RS_SESSION=abc; Path=/crm-rs; HttpOnly; SameSite=Lax");
    }
}
