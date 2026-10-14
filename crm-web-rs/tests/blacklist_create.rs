//! Nový záznam (Java `BlacklistCreateWebTest`).

mod common;

use axum::http::{Method, StatusCode};
use common::{Browser, CITATEL, SPRAVCA, test_app};

const VALID: &str = "firstName=Nov%C3%BD&lastName=%C4%8Clovek&birthDate=1990-01-01&stateReason=DEBT\
&conditionType=REQUIRE_DEPOSIT&validFrom=2026-10-14&validTo=&identifierType=ID_CARD&identifierValue=AB654321";

/// Formulár: „platné od“ = dnes, všetky podmienky, AJAX URL obnovy podmienok.
#[tokio::test]
async fn should_render_create_form_with_today_when_opened() {
    let app = test_app();
    let page = Browser::new(&app.router, Some(SPRAVCA)).get("/crm-rs/blacklist/create").await;
    assert_eq!(page.status, StatusCode::OK);
    assert!(page.body.contains(r#"<input id="validFrom" name="validFrom" type="date" value="2026-10-14"/>"#));
    assert!(page.body.contains(r#"data-ajax-refresh="/crm-rs/blacklist/resource/refreshCreateConditionTypes""#));
    for condition in ["BLOCK_ALL", "REQUIRE_DEPOSIT", "MANUAL_APPROVAL"] {
        assert!(page.body.contains(&format!(r#"<option value="{condition}">"#)), "{condition}");
    }
}

/// Čitateľ nový záznam nevytvorí ani nevidí formulár.
#[tokio::test]
async fn should_forbid_create_when_reader() {
    let app = test_app();
    let mut browser = Browser::new(&app.router, Some(CITATEL));
    assert_eq!(browser.get("/crm-rs/blacklist/create").await.status, StatusCode::FORBIDDEN);
    assert_eq!(browser.post("/crm-rs/blacklist/createAction", VALID).await.status, StatusCode::FORBIDDEN);
    assert_eq!(app.state.blacklist.find_by_id(9), None);
}

/// AJAX obnova podmienok podľa dôvodu; prázdny dôvod = všetky, neznámy = 400.
#[tokio::test]
async fn should_refresh_condition_types_when_reason_changes() {
    let app = test_app();
    let mut browser = Browser::new(&app.router, Some(SPRAVCA));
    let debt = browser.ajax(Method::GET, "/crm-rs/blacklist/resource/refreshCreateConditionTypes?stateReason=DEBT").await;
    assert!(debt.body.trim_start().starts_with(r#"<select id="conditionType" name="conditionType">"#));
    assert!(debt.body.contains("REQUIRE_DEPOSIT") && debt.body.contains("MANUAL_APPROVAL"));
    assert!(!debt.body.contains("BLOCK_ALL"));
    let any = browser.ajax(Method::GET, "/crm-rs/blacklist/resource/refreshCreateConditionTypes?stateReason=").await;
    assert!(any.body.contains("BLOCK_ALL"));
    let unknown = browser.ajax(Method::GET, "/crm-rs/blacklist/resource/refreshCreateConditionTypes?stateReason=XYZ").await;
    assert_eq!(unknown.status, StatusCode::BAD_REQUEST);
}

/// Platný formulár: záznam vznikne, PRG na jeho detail s hláškou.
#[tokio::test]
async fn should_create_and_redirect_to_detail_when_valid() {
    let app = test_app();
    let mut browser = Browser::new(&app.router, Some(SPRAVCA));
    let action = browser.post("/crm-rs/blacklist/createAction", VALID).await;
    assert_eq!((action.status, action.location()), (StatusCode::FOUND, "/crm-rs/blacklist/detail?detailId=9"));
    let detail = browser.get("/crm-rs/blacklist/detail?detailId=9").await;
    assert!(detail.body.contains("Záznam bol vytvorený."));
    assert!(detail.body.contains("Detail záznamu: Nový Človek"));
}

/// Neplatný formulár: chyby pri poliach, zadané hodnoty ostanú, nič sa nevytvorí.
#[tokio::test]
async fn should_show_field_errors_and_keep_values_when_invalid() {
    let app = test_app();
    let mut browser = Browser::new(&app.router, Some(SPRAVCA));
    let empty = browser.post("/crm-rs/blacklist/createAction", "firstName=Nov%C3%BD").await;
    assert_eq!(empty.status, StatusCode::OK);
    assert!(empty.body.contains(r#"<input id="firstName" name="firstName" type="text" value="Nový"/>"#));
    assert!(empty.body.contains(r#"<span id="lastName.errors" class="field-error">Povinné pole.</span>"#));
    assert!(!empty.body.contains("firstName.errors"));
    let rules = VALID.replace("birthDate=1990-01-01", "birthDate=2030-01-01")
        .replace("validTo=", "validTo=2026-10-01").replace("AB654321", "12")
        .replace("stateReason=DEBT", "stateReason=FRAUD");
    let page = browser.post("/crm-rs/blacklist/createAction", &rules).await;
    assert!(page.body.contains("Dátum musí byť v minulosti."));
    assert!(page.body.contains(r#"id="periodValid.errors""#));
    assert!(page.body.contains(r#"id="identifierValid.errors""#));
    assert!(page.body.contains("Táto podmienka nie je pre zvolený dôvod povolená."));
    assert!(page.body.contains(r#"<option value="FRAUD" selected="selected">"#));
    assert_eq!(app.state.blacklist.find_by_id(9), None);
}
