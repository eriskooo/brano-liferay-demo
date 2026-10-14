//! Úložisko zdieľanej session (Redis hash `crm:session:{SSO_ID}`, hodnoty sú JSON reťazce) – Java
//! `SharedSessionStore`. Kontrakt medzi appkami tvorí len názov hashu, polia a JSON tvar hodnôt.

use std::collections::HashMap;
use std::fmt;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Mutex, MutexGuard, PoisonError};
use std::time::Duration;

use redis::aio::MultiplexedConnection;

use crate::BoxFuture;

const KEY_PREFIX: &str = "crm:session:";
const FLAG_KEY: &str = "crm:flags:session-read-source";
const TTL_SECONDS: i64 = 30 * 60;
/// Ako Java `spring.data.redis.timeout=500ms`: pri výpadku stránka zlyhá rýchlo a beží ďalej bez session.
const TIMEOUT: Duration = Duration::from_millis(500);

/// Chyba úložiska (výpadok, timeout) – volajúci ju zaloguje a pokračuje bez zdieľanej session.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StoreError(pub String);

impl fmt::Display for StoreError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "zdieľaná session: {}", self.0)
    }
}

impl std::error::Error for StoreError {}

/// Port na zdieľanú session všetkých appiek.
pub trait SharedSessionStore: Send + Sync {
    /// JSON hodnota poľa; obnoví TTL hashu.
    fn get_json<'a>(&'a self, sso_id: &'a str, key: &'a str) -> BoxFuture<'a, Result<Option<String>, StoreError>>;
    /// Zapíše JSON hodnotu poľa; obnoví TTL hashu.
    fn put_json<'a>(&'a self, sso_id: &'a str, key: &'a str, json: String) -> BoxFuture<'a, Result<(), StoreError>>;
    /// Odstráni pole.
    fn remove<'a>(&'a self, sso_id: &'a str, key: &'a str) -> BoxFuture<'a, Result<(), StoreError>>;
    /// Flag zdroja čítania starej appky – Rust appka ho len zobrazuje.
    fn legacy_read_source(&self) -> BoxFuture<'_, Result<Option<String>, StoreError>>;
}

fn lock<T>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    mutex.lock().unwrap_or_else(PoisonError::into_inner)
}

/// In-memory úložisko pre testy (Java `InMemorySharedSessionStore`), vie simulovať výpadok.
#[derive(Default)]
pub struct InMemorySharedSessionStore {
    hashes: Mutex<HashMap<String, HashMap<String, String>>>,
    legacy_flag: Mutex<Option<String>>,
    failing: AtomicBool,
}

impl InMemorySharedSessionStore {
    /// Zapne/vypne simulovaný výpadok (všetky operácie vrátia chybu).
    pub fn set_failing(&self, failing: bool) {
        self.failing.store(failing, Ordering::SeqCst);
    }

    /// Nastaví flag starej appky.
    pub fn set_legacy_flag(&self, value: Option<&str>) {
        *lock(&self.legacy_flag) = value.map(String::from);
    }

    /// Surová hodnota poľa (overenie JSON v testoch).
    pub fn field(&self, sso_id: &str, key: &str) -> Option<String> {
        lock(&self.hashes).get(sso_id)?.get(key).cloned()
    }

    /// Zapíše pole priamo – simuluje zápis inej appky.
    pub fn put_field(&self, sso_id: &str, key: &str, json: &str) {
        lock(&self.hashes).entry(sso_id.to_string()).or_default().insert(key.to_string(), json.to_string());
    }

    fn check(&self) -> Result<(), StoreError> {
        if self.failing.load(Ordering::SeqCst) {
            return Err(StoreError("simulovaný výpadok".to_string()));
        }
        Ok(())
    }
}

impl SharedSessionStore for InMemorySharedSessionStore {
    fn get_json<'a>(&'a self, sso_id: &'a str, key: &'a str) -> BoxFuture<'a, Result<Option<String>, StoreError>> {
        Box::pin(async move {
            self.check()?;
            Ok(self.field(sso_id, key))
        })
    }

    fn put_json<'a>(&'a self, sso_id: &'a str, key: &'a str, json: String) -> BoxFuture<'a, Result<(), StoreError>> {
        Box::pin(async move {
            self.check()?;
            self.put_field(sso_id, key, &json);
            Ok(())
        })
    }

    fn remove<'a>(&'a self, sso_id: &'a str, key: &'a str) -> BoxFuture<'a, Result<(), StoreError>> {
        Box::pin(async move {
            self.check()?;
            if let Some(hash) = lock(&self.hashes).get_mut(sso_id) {
                hash.remove(key);
            }
            Ok(())
        })
    }

    fn legacy_read_source(&self) -> BoxFuture<'_, Result<Option<String>, StoreError>> {
        Box::pin(async move {
            self.check()?;
            Ok(lock(&self.legacy_flag).clone())
        })
    }
}

/// Redis implementácia – rovnaké kľúče a TTL ako Java (`StringRedisTemplate`) aj stará appka (Jedis).
pub struct RedisSharedSessionStore {
    client: redis::Client,
    connection: tokio::sync::Mutex<Option<MultiplexedConnection>>,
}

impl RedisSharedSessionStore {
    /// Klient bez pripojenia – appka naštartuje aj bez Redisu, spojí sa pri prvom použití.
    pub fn new(url: &str) -> Result<Self, StoreError> {
        let client = redis::Client::open(url).map_err(|e| StoreError(e.to_string()))?;
        Ok(Self { client, connection: tokio::sync::Mutex::new(None) })
    }

    /// Pipeline s timeoutom. Spadnuté spojenie (reštart Redisu) sa hneď nahradí novým a príkaz sa raz zopakuje –
    /// ako Lettuce v Jave; inak by prvé čítanie hlášok vrátilo prázdno a push by prepísal hlášky inej appky.
    /// Po neúspechu sa spojenie zahodí, aby ďalší request skúsil nové.
    async fn query<T: redis::FromRedisValue>(&self, pipeline: redis::Pipeline) -> Result<T, StoreError> {
        let attempt = async {
            let mut connection = self.connection().await?;
            match pipeline.query_async::<T>(&mut connection).await {
                Err(e) if e.is_connection_dropped() || e.is_io_error() => {
                    *self.connection.lock().await = None;
                    let mut fresh = self.connection().await?;
                    pipeline.query_async::<T>(&mut fresh).await.map_err(|e| StoreError(e.to_string()))
                }
                result => result.map_err(|e| StoreError(e.to_string())),
            }
        };
        let result = tokio::time::timeout(TIMEOUT, attempt)
            .await
            .unwrap_or_else(|_| Err(StoreError("timeout".to_string())));
        if result.is_err() {
            *self.connection.lock().await = None;
        }
        result
    }

    async fn connection(&self) -> Result<MultiplexedConnection, StoreError> {
        let mut guard = self.connection.lock().await;
        if let Some(connection) = guard.as_ref() {
            return Ok(connection.clone());
        }
        let connection = self.client.get_multiplexed_async_connection().await
            .map_err(|e| StoreError(e.to_string()))?;
        *guard = Some(connection.clone());
        Ok(connection)
    }
}

impl SharedSessionStore for RedisSharedSessionStore {
    fn get_json<'a>(&'a self, sso_id: &'a str, key: &'a str) -> BoxFuture<'a, Result<Option<String>, StoreError>> {
        Box::pin(async move {
            let hash = format!("{KEY_PREFIX}{sso_id}");
            let mut pipeline = redis::pipe();
            pipeline.cmd("HGET").arg(&hash).arg(key).cmd("EXPIRE").arg(&hash).arg(TTL_SECONDS).ignore();
            let (value,): (Option<String>,) = self.query(pipeline).await?;
            Ok(value)
        })
    }

    fn put_json<'a>(&'a self, sso_id: &'a str, key: &'a str, json: String) -> BoxFuture<'a, Result<(), StoreError>> {
        Box::pin(async move {
            let hash = format!("{KEY_PREFIX}{sso_id}");
            let mut pipeline = redis::pipe();
            pipeline.cmd("HSET").arg(&hash).arg(key).arg(json).ignore()
                .cmd("EXPIRE").arg(&hash).arg(TTL_SECONDS).ignore();
            self.query::<()>(pipeline).await
        })
    }

    fn remove<'a>(&'a self, sso_id: &'a str, key: &'a str) -> BoxFuture<'a, Result<(), StoreError>> {
        Box::pin(async move {
            let mut pipeline = redis::pipe();
            pipeline.cmd("HDEL").arg(format!("{KEY_PREFIX}{sso_id}")).arg(key).ignore();
            self.query::<()>(pipeline).await
        })
    }

    fn legacy_read_source(&self) -> BoxFuture<'_, Result<Option<String>, StoreError>> {
        Box::pin(async move {
            let mut pipeline = redis::pipe();
            pipeline.cmd("GET").arg(FLAG_KEY);
            let (value,): (Option<String>,) = self.query(pipeline).await?;
            Ok(value)
        })
    }
}
