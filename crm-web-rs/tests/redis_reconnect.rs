//! Obnova spojenia po reštarte Redisu. Samostatný súbor (vlastná testovacia binárka): CLIENT KILL zabije všetky
//! spojenia, paralelné testy v redis_store.rs by inak padali.

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

/// Spadnuté spojenie (reštart Redisu) sa obnoví hneď v tej istej operácii – inak by prvé čítanie hlášok vrátilo
/// prázdno a nasledný push by prepísal čakajúce hlášky inej appky.
#[tokio::test]
async fn should_reconnect_within_same_operation_when_connection_dropped() {
    let Some(url) = redis_url() else { return };
    let store = RedisSharedSessionStore::new(&url).expect("klient");
    store.put_json("rs-test-3", "APP_Z", "\"pred\"".to_string()).await.expect("zápis");
    let mut admin = raw(&url).await;
    let _: i64 = redis::cmd("CLIENT").arg("KILL").arg("TYPE").arg("normal").arg("SKIPME").arg("yes")
        .query_async(&mut admin).await.expect("CLIENT KILL");
    assert_eq!(store.get_json("rs-test-3", "APP_Z").await.expect("čítanie po páde spojenia").as_deref(), Some("\"pred\""));
}
