//! Prihlásenie, ACL, chybové stránky, layout a statické súbory (Java `WebInfrastructureTest`, `LayoutRenderingTest`).

mod common;

use std::sync::atomic::Ordering;

use axum::Router;
use axum::http::{Method, StatusCode};
use axum::routing::get;
use common::{Browser, CITATEL, SPRAVCA, SSO_DOWN, TestApp, test_app};
use crm_web_rs::web::portlet::{Modify, Require, View};

/// Testovacie „portletové“ handlery – overujú extraktory bez závislosti na stránkach blacklistu.
fn probe(app: &TestApp) -> Router {
    Router::new()
        .route("/crm-rs/blacklist/probe",
            get(|Require(ctx, _): Require<View>| async move { ctx.identity.operator.login.clone() })
                .post(|Require(ctx, _): Require<View>| async move { ctx.identity.operator.login.clone() }))
        .route("/crm-rs/blacklist/probe-modify",
            get(|Require(ctx, _): Require<Modify>| async move { ctx.identity.operator.login.clone() }))
        .with_state(app.state.clone())
}

/// Koreň appky a /blacklist presmerujú na vyhľadávanie (bez prihlásenia).
#[tokio::test]
async fn should_redirect_to_search_when_home_opened() {
    let app = test_app();
    for path in ["/crm-rs", "/crm-rs/", "/crm-rs/blacklist"] {
        let response = Browser::new(&app.router, None).get(path).await;
        assert_eq!((response.status, response.location()), (StatusCode::FOUND, "/crm-rs/blacklist/search"), "{path}");
    }
}

/// Bez SSO identity: GET → SSO login s návratom na pôvodnú cestu, POST → na stránku portletu, AJAX → 401.
#[tokio::test]
async fn should_require_login_when_no_identity() {
    let app = test_app();
    let router = probe(&app);
    let get = Browser::new(&router, None).get("/crm-rs/blacklist/probe?x=1").await;
    assert_eq!(get.status, StatusCode::FOUND);
    assert_eq!(get.location(), "/sso/login?returnUrl=%2Fcrm-rs%2Fblacklist%2Fprobe%3Fx%3D1");
    let post = Browser::new(&router, None).post("/crm-rs/blacklist/probe", "a=1").await;
    assert_eq!(post.location(), "/sso/login?returnUrl=%2Fcrm-rs%2Fblacklist");
    let ajax = Browser::new(&router, None).ajax(Method::GET, "/crm-rs/blacklist/probe").await;
    assert_eq!((ajax.status, ajax.body.as_str()), (StatusCode::UNAUTHORIZED, ""));
}

/// Poškodená SSO cookie = neprihlásený a SSO sa vôbec nevolá; platná sa pýta raz za request.
#[tokio::test]
async fn should_call_sso_once_and_skip_malformed_cookie() {
    let app = test_app();
    let router = probe(&app);
    let malformed = Browser::new(&router, Some("../x")).get("/crm-rs/blacklist/probe").await;
    assert_eq!(malformed.status, StatusCode::FOUND);
    assert_eq!(app.sso.calls.load(Ordering::SeqCst), 0);
    let ok = Browser::new(&router, Some(SPRAVCA)).get("/crm-rs/blacklist/probe").await;
    assert_eq!((ok.status, ok.body.as_str()), (StatusCode::OK, "spravca"));
    assert_eq!(app.sso.calls.load(Ordering::SeqCst), 1);
}

/// Nedostupné SSO je 503 s chybovou stránkou, nie „prihláste sa“.
#[tokio::test]
async fn should_render_sso_error_when_portal_down() {
    let app = test_app();
    let response = Browser::new(&probe(&app), Some(SSO_DOWN)).get("/crm-rs/blacklist/probe").await;
    assert_eq!(response.status, StatusCode::SERVICE_UNAVAILABLE);
    assert!(response.body.contains("SSO portál je nedostupný"));
}

/// Chýbajúce privilégium: 403 stránka s operátorom v hlavičke; AJAX dostane fragment bez layoutu.
#[tokio::test]
async fn should_render_403_when_privilege_missing() {
    let app = test_app();
    let router = probe(&app);
    let page = Browser::new(&router, Some(CITATEL)).get("/crm-rs/blacklist/probe-modify").await;
    assert_eq!(page.status, StatusCode::FORBIDDEN);
    assert!(page.body.contains("Prístup zamietnutý"));
    assert!(page.body.contains("<code>BLACKLIST_MODIFY</code>"));
    assert!(page.body.contains(r#"<span id="headerOperator">Operátor: Čitateľ</span>"#));
    let fragment = Browser::new(&router, Some(CITATEL)).ajax(Method::GET, "/crm-rs/blacklist/probe-modify").await;
    assert_eq!(fragment.status, StatusCode::FORBIDDEN);
    assert!(fragment.body.contains(r#"class="field-error""#));
    assert!(!fragment.body.contains("<html"));
}

/// Neznáma cesta: vlastná 404 stránka s layoutom, navigáciou do Liferay a CSS z Liferay theme.
#[tokio::test]
async fn should_render_custom_404_when_path_unknown() {
    let app = test_app();
    let response = Browser::new(&app.router, Some(SPRAVCA)).get("/crm-rs/neexistuje").await;
    assert_eq!(response.status, StatusCode::NOT_FOUND);
    assert!(response.body.contains("Nenájdené"));
    assert!(response.body.contains("Operátor: Správca"));
    assert!(response.body.contains(r#"<a href="/web/crm/dashboard">Dashboard</a>"#));
    assert!(response.body.contains(r#"href="/html/themes/classic/css/main.css"#));
    assert!(response.body.contains(r#"href="/crm-rs/css/crm.css""#));
    assert!(response.body.contains("<title>Nenájdené &ndash; CRM – nová appka (Rust)</title>"));
}

/// Layout: hláška zapísaná inou appkou sa zobrazí raz, ladiaci riadok ukáže flag starej appky a SSO_ID.
#[tokio::test]
async fn should_show_shared_session_in_layout_when_page_rendered() {
    let app = test_app();
    app.store.put_field(SPRAVCA, "APP_MESSAGES", r#"[{"level":"INFO","key":"dashboard.deactivation.submitted"}]"#);
    app.store.put_field(SPRAVCA, "APP_LAST_VIEWED_RECORD", r#"{"id":3,"fullName":"Ján Testovací"}"#);
    app.store.set_legacy_flag(Some("REDIS"));
    let mut browser = Browser::new(&app.router, Some(SPRAVCA));
    let first = browser.get("/crm-rs/x").await;
    assert!(first.body.contains(r#"<div class="portlet-msg-success crm-message">Žiadosť o zrušenie bola prijatá.</div>"#));
    assert!(first.body.contains("Naposledy otvorený záznam: Ján Testovací"));
    assert!(first.body.contains("zdroj čítania = Redis (Rust appka)"));
    assert!(first.body.contains("flag starej appky = REDIS"));
    assert!(first.body.contains("SSO_ID = sso-spravca"));
    assert!(!browser.get("/crm-rs/x").await.body.contains("Žiadosť o zrušenie"));
}

/// Výpadok Redisu stránku nezhodí (bez hlášok, flag = PORTLET).
#[tokio::test]
async fn should_render_page_when_shared_store_fails() {
    let app = test_app();
    app.store.set_failing(true);
    let response = Browser::new(&app.router, Some(SPRAVCA)).get("/crm-rs/x").await;
    assert_eq!(response.status, StatusCode::NOT_FOUND);
    assert!(response.body.contains("Operátor: Správca"));
    assert!(response.body.contains("flag starej appky = PORTLET"));
}

/// Statické súbory (náhrada theme) sú pod kontextom /crm-rs a idú priamo z crm-web.
#[tokio::test]
async fn should_serve_static_assets_when_requested_under_context_path() {
    let app = test_app();
    let css = Browser::new(&app.router, None).get("/crm-rs/css/crm.css").await;
    assert_eq!(css.status, StatusCode::OK);
    assert!(css.headers["content-type"].to_str().unwrap_or("").starts_with("text/css"));
    let js = Browser::new(&app.router, None).get("/crm-rs/js/crm.js").await;
    assert!(js.body.contains("data-ajax-post"));
}
