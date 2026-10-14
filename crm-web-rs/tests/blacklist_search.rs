//! Vyhľadávanie v blackliste (Java `BlacklistSearchWebTest` + časť `SharedSessionFlowTest`).

mod common;

use axum::http::StatusCode;
use common::{Browser, CITATEL, SPRAVCA, test_app};

const EMPTY: &str = "firstName=&lastName=&identifierValue=&state=";

/// Bez SSO cookie presmeruje na SSO login s návratom na vyhľadávanie.
#[tokio::test]
async fn should_redirect_to_sso_login_when_no_sso_cookie() {
    let app = test_app();
    let response = Browser::new(&app.router, None).get("/crm-rs/blacklist/search").await;
    assert_eq!(response.status, StatusCode::FOUND);
    assert_eq!(response.location(), "/sso/login?returnUrl=%2Fcrm-rs%2Fblacklist%2Fsearch");
}

/// Formulár ako legacy JSP: akcia, polia, Rust názov appky; „Nový záznam“ len pre správcu.
#[tokio::test]
async fn should_render_search_form_when_logged_in() {
    let app = test_app();
    let page = Browser::new(&app.router, Some(SPRAVCA)).get("/crm-rs/blacklist/search").await;
    assert_eq!(page.status, StatusCode::OK);
    assert!(page.body.contains(r#"<form id="searchForm" action="/crm-rs/blacklist/searchAction" method="post">"#));
    assert!(page.body.contains(r#"<input id="firstName" name="firstName" type="text" value=""/>"#));
    assert!(page.body.contains(r#"<option value="ACTIVE">Aktívny</option>"#));
    assert!(page.body.contains(r#"action="/crm-rs/blacklist/newSearch""#));
    assert!(page.body.contains(r#"id="gotoCreateForm""#));
    let reader = Browser::new(&app.router, Some(CITATEL)).get("/crm-rs/blacklist/search").await;
    assert!(!reader.body.contains(r#"id="gotoCreateForm""#));
}

/// Prázdny formulár: chyba celého formulára, nič sa nezdieľa.
#[tokio::test]
async fn should_show_no_criteria_error_when_form_empty() {
    let app = test_app();
    let page = Browser::new(&app.router, Some(SPRAVCA)).post("/crm-rs/blacklist/searchAction", EMPTY).await;
    assert_eq!(page.status, StatusCode::OK);
    assert!(page.body.contains(r#"<div id="searchCriteria.errors" class="field-error">Zadajte aspoň jedno kritérium.</div>"#));
    assert_eq!(app.store.field(SPRAVCA, "APP_BLACKLIST_LAST_CRITERIA"), None);
}

/// Diakritika: PRG na výsledky, kritériá v zdieľanej session (JSON pre Java/legacy), vlastná cookie /crm-rs.
#[tokio::test]
async fn should_find_diacritics_and_share_criteria_when_posted() {
    let app = test_app();
    let mut browser = Browser::new(&app.router, Some(SPRAVCA));
    let action = browser.post("/crm-rs/blacklist/searchAction", "firstName=M%C3%A1ria&lastName=&identifierValue=&state=").await;
    assert_eq!((action.status, action.location()), (StatusCode::FOUND, "/crm-rs/blacklist/searchresult"));
    let cookie = action.set_cookie("CRM_RS_SESSION").expect("lokálna session");
    assert!(cookie.contains("Path=/crm-rs") && cookie.contains("HttpOnly"), "{cookie}");
    assert_eq!(app.store.field(SPRAVCA, "APP_BLACKLIST_LAST_CRITERIA").as_deref(),
        Some(r#"{"firstName":"Mária","lastName":"","identifierValue":"","state":null}"#));
    let result = browser.get("/crm-rs/blacklist/searchresult").await;
    assert!(result.body.contains(r#"<p id="resultCount">Počet záznamov: 1</p>"#));
    assert!(result.body.contains(r#"<a id="detail-2" href="/crm-rs/blacklist/detail?detailId=2">Mária Príkladová</a>"#));
    assert!(result.body.contains("2023-06-01 &ndash;"));
}

/// Neodoslané pole ostane z predošlého hľadania (@SessionAttributes); formulár ukáže uložené hodnoty.
#[tokio::test]
async fn should_keep_previous_criteria_when_field_not_posted() {
    let app = test_app();
    let mut browser = Browser::new(&app.router, Some(SPRAVCA));
    browser.post("/crm-rs/blacklist/searchAction", "firstName=Peter&lastName=&identifierValue=&state=").await;
    browser.post("/crm-rs/blacklist/searchAction", "lastName=Vzork").await;
    let result = browser.get("/crm-rs/blacklist/searchresult").await;
    assert!(result.body.contains("Peter Vzorka") && result.body.contains("Peter Vzorkový"));
    assert!(result.body.contains("Počet záznamov: 2"));
    let form = browser.get("/crm-rs/blacklist/search").await;
    assert!(form.body.contains(r#"<input id="firstName" name="firstName" type="text" value="Peter"/>"#));
}

/// Bez výsledku: späť na vyhľadávanie s hláškou, ktorá sa zobrazí raz.
#[tokio::test]
async fn should_show_no_result_message_once_when_nothing_found() {
    let app = test_app();
    let mut browser = Browser::new(&app.router, Some(SPRAVCA));
    let action = browser.post("/crm-rs/blacklist/searchAction", "lastName=Neexistuje").await;
    assert_eq!(action.location(), "/crm-rs/blacklist/search");
    assert!(browser.get("/crm-rs/blacklist/search").await.body.contains("Nenašiel sa žiadny záznam."));
    assert!(!browser.get("/crm-rs/blacklist/search").await.body.contains("Nenašiel sa žiadny záznam."));
}

/// „Vymazať“ zahodí kritériá zo session; neznámy stav je chyba poľa.
#[tokio::test]
async fn should_clear_criteria_and_reject_unknown_state() {
    let app = test_app();
    let mut browser = Browser::new(&app.router, Some(SPRAVCA));
    browser.post("/crm-rs/blacklist/searchAction", "firstName=Peter").await;
    let clear = browser.get("/crm-rs/blacklist/newSearch").await;
    assert_eq!(clear.location(), "/crm-rs/blacklist/search");
    assert!(browser.get("/crm-rs/blacklist/search").await.body
        .contains(r#"<input id="firstName" name="firstName" type="text" value=""/>"#));
    let invalid = browser.post("/crm-rs/blacklist/searchAction", "state=XYZ").await;
    assert!(invalid.body.contains("Neplatná hodnota."));
}

/// Výsledky: neobmedzená platnosť sa vypíše textom, stav i18n.
#[tokio::test]
async fn should_render_validity_and_state_when_results_listed() {
    let app = test_app();
    let mut browser = Browser::new(&app.router, Some(SPRAVCA));
    browser.post("/crm-rs/blacklist/searchAction", "state=ACTIVE").await;
    let result = browser.get("/crm-rs/blacklist/searchresult").await;
    assert!(result.body.contains("Počet záznamov: 6"));
    assert!(result.body.contains("neobmedzene"));
    assert!(result.body.contains("<td>Aktívny</td>"));
    assert!(result.body.contains(r#"href="/crm-rs/blacklist/search">Nové vyhľadávanie</a>"#));
}
