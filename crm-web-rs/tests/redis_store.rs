//! RedisSharedSessionStore proti skutočnému Redisu z crm-web-rs/test.sh (REDIS_URL); bez neho sa preskočí.

use std::time::{Duration, Instant};

use crm_web_rs::session::store::{RedisSharedSessionStore, SharedSessionStore};

fn redis_url() -> Option<String> {
    let url = std::env::var("REDIS_URL").ok().filter(|v| !v.is_empty());
    if url.is_none() {
        eprintln!("REDIS_URL chýba – test preskočený (spusti cez crm-web-rs/test.sh)");
    }
    url
}

async fn raw(url: &str) -> redis::aio::MultiplexedConnection {
    redis::Client::open(url).expect("URL").get_multiplexed_async_connection().await.expect("spojenie")
}

/// Zápis ide do hashu crm:session:{SSO_ID} s TTL 30 minút, čítanie vráti tú istú hodnotu.
#[tokio::test]
async fn should_write_hash_with_ttl_when_put() {
    let Some(url) = redis_url() else { return };
    let store = RedisSharedSessionStore::new(&url).expect("klient");
    store.put_json("rs-test-1", "APP_X", r#"{"a":1}"#.to_string()).await.expect("zápis");
    let mut connection = raw(&url).await;
    let value: Option<String> = redis::cmd("HGET").arg("crm:session:rs-test-1").arg("APP_X")
        .query_async(&mut connection).await.expect("HGET");
    let ttl: i64 = redis::cmd("TTL").arg("crm:session:rs-test-1").query_async(&mut connection).await.expect("TTL");
    assert_eq!(value.as_deref(), Some(r#"{"a":1}"#));
    assert!((1700..=1800).contains(&ttl), "TTL {ttl}");
    assert_eq!(store.get_json("rs-test-1", "APP_X").await.expect("čítanie").as_deref(), Some(r#"{"a":1}"#));
}

/// Odstránenie poľa a flag starej appky (crm:flags:session-read-source).
#[tokio::test]
async fn should_remove_field_and_read_legacy_flag_when_asked() {
    let Some(url) = redis_url() else { return };
    let store = RedisSharedSessionStore::new(&url).expect("klient");
    store.put_json("rs-test-2", "APP_Y", "1".to_string()).await.expect("zápis");
    store.remove("rs-test-2", "APP_Y").await.expect("odstránenie");
    assert_eq!(store.get_json("rs-test-2", "APP_Y").await.expect("čítanie"), None);
    let mut connection = raw(&url).await;
    let _: () = redis::cmd("SET").arg("crm:flags:session-read-source").arg("REDIS")
        .query_async(&mut connection).await.expect("SET");
    assert_eq!(store.legacy_read_source().await.expect("flag").as_deref(), Some("REDIS"));
    let _: () = redis::cmd("DEL").arg("crm:flags:session-read-source")
        .query_async(&mut connection).await.expect("DEL");
}

/// Nedostupný Redis vráti chybu rýchlo (timeout 500 ms) aj pri opakovanom pokuse, request sa nezasekne.
#[tokio::test]
async fn should_fail_fast_when_redis_unreachable() {
    let store = RedisSharedSessionStore::new("redis://127.0.0.1:1/").expect("klient");
    let started = Instant::now();
    assert!(store.get_json("x", "APP_X").await.is_err());
    assert!(store.get_json("x", "APP_X").await.is_err());
    assert!(started.elapsed() < Duration::from_secs(2), "trvalo {:?}", started.elapsed());
}
