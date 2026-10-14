//! Detail záznamu (Java `BlacklistDetailWebTest`).

mod common;

use axum::http::{Method, StatusCode};
use common::{Browser, CITATEL, SPRAVCA, test_app};
use crm_web_rs::blacklist::model::BlacklistState;

/// Detail ukáže záznam, poznámky a zapíše posledný záznam do zdieľanej session (hlavička ho hneď ukáže).
#[tokio::test]
async fn should_render_detail_and_share_last_viewed_when_opened() {
    let app = test_app();
    let page = Browser::new(&app.router, Some(SPRAVCA)).get("/crm-rs/blacklist/detail?detailId=1").await;
    assert_eq!(page.status, StatusCode::OK);
    assert!(page.body.contains("<h2>Detail záznamu: Peter Vzorka</h2>"));
    assert!(page.body.contains("Občiansky preukaz: AB123456"));
    assert!(page.body.contains("<li>2024-01-10T09:00 Systém: Opakované pokusy o objednávku na cudzie doklady.</li>"));
    assert!(page.body.contains(r#"<td id="recordValidTo">"#) && page.body.contains("neobmedzene"));
    assert!(page.body.contains("Naposledy otvorený záznam: Peter Vzorka"));
    assert!(page.body.contains(r#"action="/crm-rs/blacklist/changeAction?detailId=1""#));
    assert_eq!(app.store.field(SPRAVCA, "APP_LAST_VIEWED_RECORD").as_deref(), Some(r#"{"id":1,"fullName":"Peter Vzorka"}"#));
}

/// Čitateľ vidí detail bez formulárov a odkazov na odstránenie.
#[tokio::test]
async fn should_hide_modifications_when_reader() {
    let app = test_app();
    let page = Browser::new(&app.router, Some(CITATEL)).get("/crm-rs/blacklist/detail?detailId=1").await;
    assert_eq!(page.status, StatusCode::OK);
    assert!(!page.body.contains(r#"id="changeForm""#));
    assert!(!page.body.contains("remove-ident-"));
}

/// Poškodené/chýbajúce ID → 400, záporné alebo neexistujúce → 404 (stránky s layoutom, žiadny pád).
#[tokio::test]
async fn should_return_400_when_detail_id_malformed() {
    let app = test_app();
    let mut browser = Browser::new(&app.router, Some(SPRAVCA));
    for path in ["/crm-rs/blacklist/detail?detailId=abc", "/crm-rs/blacklist/detail"] {
        let response = browser.get(path).await;
        assert_eq!(response.status, StatusCode::BAD_REQUEST, "{path}");
        assert!(response.body.contains("Chyba 400"));
    }
    for path in ["/crm-rs/blacklist/detail?detailId=-1", "/crm-rs/blacklist/detail?detailId=999"] {
        let response = browser.get(path).await;
        assert_eq!(response.status, StatusCode::NOT_FOUND, "{path}");
        assert!(response.body.contains("Operátor: Správca"));
    }
}

/// Zmena stavu: PRG na detail, hláška raz, deaktivácia bez dátumu doplní dnešok.
#[tokio::test]
async fn should_change_state_and_show_message_once_when_valid() {
    let app = test_app();
    let mut browser = Browser::new(&app.router, Some(SPRAVCA));
    let action = browser.post("/crm-rs/blacklist/changeAction?detailId=7", "state=INACTIVE&validTo=").await;
    assert_eq!((action.status, action.location()), (StatusCode::FOUND, "/crm-rs/blacklist/detail?detailId=7"));
    let detail = browser.get("/crm-rs/blacklist/detail?detailId=7").await;
    assert!(detail.body.contains("Záznam bol zmenený."));
    assert!(detail.body.contains(r#"<td id="recordState">Neaktívny</td>"#));
    assert!(detail.body.contains("2026-10-14"));
    assert!(!browser.get("/crm-rs/blacklist/detail?detailId=7").await.body.contains("Záznam bol zmenený."));
}

/// Chýbajúci stav: detail sa vykreslí znova s chybou pri poli (bez redirectu).
#[tokio::test]
async fn should_rerender_with_error_when_state_missing() {
    let app = test_app();
    let page = Browser::new(&app.router, Some(SPRAVCA)).post("/crm-rs/blacklist/changeAction?detailId=1", "state=&validTo=").await;
    assert_eq!(page.status, StatusCode::OK);
    assert!(page.body.contains(r#"<span id="state.errors" class="field-error">Povinné pole.</span>"#));
}

/// Čitateľ nesmie meniť: 403 a záznam ostane nezmenený.
#[tokio::test]
async fn should_forbid_change_when_reader() {
    let app = test_app();
    let response = Browser::new(&app.router, Some(CITATEL)).post("/crm-rs/blacklist/changeAction?detailId=7", "state=INACTIVE").await;
    assert_eq!(response.status, StatusCode::FORBIDDEN);
    assert!(response.body.contains("BLACKLIST_MODIFY"));
    assert_eq!(app.state.blacklist.find_by_id(7).expect("záznam").state, BlacklistState::Active);
}

/// HTML v poznámke sa na stránke escapuje; autor je operátor zo SSO.
#[tokio::test]
async fn should_escape_html_when_note_contains_markup() {
    let app = test_app();
    let mut browser = Browser::new(&app.router, Some(SPRAVCA));
    let action = browser.post("/crm-rs/blacklist/addNoteAction?detailId=2", "text=%3Cb%3Ex%3C%2Fb%3E").await;
    assert_eq!(action.location(), "/crm-rs/blacklist/detail?detailId=2");
    let detail = browser.get("/crm-rs/blacklist/detail?detailId=2").await;
    assert!(detail.body.contains("Poznámka bola pridaná."));
    let note_line = detail.body.lines().find(|l| l.contains("Správca:")).unwrap_or("");
    assert!(detail.body.contains("Správca: &#60;b&#62;x&#60;/b&#62;"), "{note_line}");
    assert!(!detail.body.contains("<b>x</b>"));
}

/// Prázdna a príliš dlhá poznámka sú chyby poľa text.
#[tokio::test]
async fn should_reject_blank_and_too_long_note() {
    let app = test_app();
    let mut browser = Browser::new(&app.router, Some(SPRAVCA));
    let blank = browser.post("/crm-rs/blacklist/addNoteAction?detailId=2", "text=+++").await;
    assert!(blank.body.contains(r#"<span id="text.errors" class="field-error">Povinné pole.</span>"#));
    let long = browser.post("/crm-rs/blacklist/addNoteAction?detailId=2", &format!("text={}", "a".repeat(501))).await;
    assert!(long.body.contains("Text je príliš dlhý."));
}

/// AJAX odstránenie identifikátora vráti fragment div#identifiers (bez layoutu).
#[tokio::test]
async fn should_remove_identifier_via_ajax_when_more_than_one() {
    let app = test_app();
    let fragment = Browser::new(&app.router, Some(SPRAVCA))
        .ajax(Method::POST, "/crm-rs/blacklist/resource/removeIdentAction?detailId=1&identId=2").await;
    assert_eq!(fragment.status, StatusCode::OK);
    assert!(fragment.body.trim_start().starts_with(r#"<div id="identifiers">"#));
    assert!(!fragment.body.contains("AB123456"));
    assert!(!fragment.body.contains("<html"));
}

/// Posledný identifikátor ostane, fragment ukáže chybu.
#[tokio::test]
async fn should_refuse_last_identifier_when_removing() {
    let app = test_app();
    let fragment = Browser::new(&app.router, Some(SPRAVCA))
        .ajax(Method::POST, "/crm-rs/blacklist/resource/removeIdentAction?detailId=2&identId=3").await;
    assert!(fragment.body.contains(r#"<div class="portlet-msg-error">Posledný identifikátor nie je možné odstrániť.</div>"#));
    assert!(fragment.body.contains("755103/2345"));
}

/// Identifikátor iného záznamu = 404, cudzie dáta sa nezmenia.
#[tokio::test]
async fn should_return_404_when_identifier_belongs_to_other_record() {
    let app = test_app();
    let response = Browser::new(&app.router, Some(SPRAVCA))
        .ajax(Method::POST, "/crm-rs/blacklist/resource/removeIdentAction?detailId=1&identId=3").await;
    assert_eq!(response.status, StatusCode::NOT_FOUND);
    assert_eq!(app.state.blacklist.find_by_id(2).expect("záznam").identifiers.len(), 1);
}

/// Mutácia len cez POST (GET = 405), AJAX bez SSO = 401, AJAX čitateľa = 403 fragment.
#[tokio::test]
async fn should_enforce_method_login_and_acl_when_removing_identifier() {
    let app = test_app();
    let path = "/crm-rs/blacklist/resource/removeIdentAction?detailId=1&identId=2";
    assert_eq!(Browser::new(&app.router, Some(SPRAVCA)).get(path).await.status, StatusCode::METHOD_NOT_ALLOWED);
    assert_eq!(Browser::new(&app.router, None).ajax(Method::POST, path).await.status, StatusCode::UNAUTHORIZED);
    let reader = Browser::new(&app.router, Some(CITATEL)).ajax(Method::POST, path).await;
    assert_eq!(reader.status, StatusCode::FORBIDDEN);
    assert!(!reader.body.contains("<html"));
    assert_eq!(app.state.blacklist.find_by_id(1).expect("záznam").identifiers.len(), 2);
}
