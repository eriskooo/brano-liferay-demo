//! SsoClient proti fake SSO serveru na náhodnom porte (Java `SsoClientTest` + `FakeSsoServer`).

use std::time::{Duration, Instant};

use axum::extract::Path;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::routing::get;
use axum::{Json, Router};
use crm_web_rs::session::identity::SsoApi;
use crm_web_rs::session::sso_client::SsoClient;

/// Fake portál s rovnakým JSON ako sso-mock; "slow" odpovie po 3 s (klient má timeout 2 s).
async fn start_fake_sso() -> String {
    async fn session(Path(id): Path<String>) -> Response {
        match id.as_str() {
            "known" => Json(serde_json::json!({
                "operator": {"login": "spravca", "displayName": "Správca"},
                "customer": {"customerId": "C-1001", "displayName": "Vzorový zákazník"},
                "extra": 1
            }))
            .into_response(),
            "broken" => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
            "slow" => {
                tokio::time::sleep(Duration::from_secs(3)).await;
                StatusCode::NOT_FOUND.into_response()
            }
            _ => StatusCode::NOT_FOUND.into_response(),
        }
    }
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.expect("voľný port");
    let address = listener.local_addr().expect("adresa");
    let router = Router::new().route("/sso/api/sessions/{id}", get(session));
    tokio::spawn(async move { axum::serve(listener, router).await.ok() });
    format!("http://{address}/sso")
}

/// Platná SSO session vráti operátora a zákazníka (neznáme pole v JSON nevadí).
#[tokio::test]
async fn should_return_identity_when_portal_knows_session() {
    let client = SsoClient::new(&start_fake_sso().await).expect("klient");
    let identity = client.fetch("known").await.expect("odpoveď").expect("identita");
    assert_eq!(identity.sso_session_id, "known");
    assert_eq!(identity.operator.login, "spravca");
    assert_eq!(identity.operator.display_name, "Správca");
    assert_eq!(identity.customer.customer_id, "C-1001");
}

/// Neznáma session (404) je „neprihlásený“, nie chyba.
#[tokio::test]
async fn should_return_none_when_portal_returns_404() {
    let client = SsoClient::new(&start_fake_sso().await).expect("klient");
    assert_eq!(client.fetch("unknown").await, Ok(None));
}

/// HTTP 500, timeout aj nedostupný portál sú „SSO nedostupné“; timeout neblokuje request dlhšie než 2 s.
#[tokio::test]
async fn should_fail_when_portal_errors_is_slow_or_unreachable() {
    let client = SsoClient::new(&start_fake_sso().await).expect("klient");
    assert!(client.fetch("broken").await.is_err());
    let started = Instant::now();
    assert!(client.fetch("slow").await.is_err());
    assert!(started.elapsed() < Duration::from_millis(2900), "trvalo {:?}", started.elapsed());
    let unreachable = SsoClient::new("http://127.0.0.1:9/sso").expect("klient");
    assert!(unreachable.fetch("known").await.is_err());
}
