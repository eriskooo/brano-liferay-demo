//! Spoločná infraštruktúra webových testov (Java `TestInfrastructure`, `FakeSsoServer`, `WebClient`): fake SSO
//! bez siete, in-memory zdieľaná session, pevné hodiny a „prehliadač“, ktorý si pamätá CRM_RS_SESSION.
#![allow(dead_code)]

use std::collections::HashMap;
use std::sync::Arc;
use std::sync::atomic::{AtomicUsize, Ordering};

use axum::Router;
use axum::body::Body;
use axum::http::header::{COOKIE, LOCATION, SET_COOKIE};
use axum::http::{HeaderMap, Method, Request, StatusCode};
use chrono::NaiveDate;
use crm_web_rs::BoxFuture;
use crm_web_rs::acl::InMemoryAclService;
use crm_web_rs::blacklist::service::InMemoryBlacklistService;
use crm_web_rs::clock::{Clock, FixedClock};
use crm_web_rs::config::Config;
use crm_web_rs::session::identity::{CustomerContext, OperatorUser, SsoApi, SsoIdentity, SsoUnavailable};
use crm_web_rs::session::local::LocalSessions;
use crm_web_rs::session::store::InMemorySharedSessionStore;
use crm_web_rs::web::messages::Messages;
use crm_web_rs::{AppState, app};
use http_body_util::BodyExt;
use tower::ServiceExt;

pub const SPRAVCA: &str = "sso-spravca";
pub const CITATEL: &str = "sso-citatel";
/// SSO_ID, pre ktoré fake portál „nefunguje“.
pub const SSO_DOWN: &str = "sso-down";

/// Fake SSO portál s demo operátormi; počíta volania (identita sa má pýtať raz za request).
pub struct FakeSso {
    identities: HashMap<String, SsoIdentity>,
    pub calls: AtomicUsize,
}

impl FakeSso {
    fn demo() -> Self {
        let identity = |id: &str, login: &str, name: &str| SsoIdentity {
            sso_session_id: id.to_string(),
            operator: OperatorUser { login: login.to_string(), display_name: name.to_string() },
            customer: CustomerContext { customer_id: "C-1001".to_string(), display_name: "Vzorový zákazník".to_string() },
        };
        let identities = HashMap::from([
            (SPRAVCA.to_string(), identity(SPRAVCA, "spravca", "Správca")),
            (CITATEL.to_string(), identity(CITATEL, "citatel", "Čitateľ")),
        ]);
        Self { identities, calls: AtomicUsize::new(0) }
    }
}

impl SsoApi for FakeSso {
    fn fetch<'a>(&'a self, sso_session_id: &'a str) -> BoxFuture<'a, Result<Option<SsoIdentity>, SsoUnavailable>> {
        Box::pin(async move {
            self.calls.fetch_add(1, Ordering::SeqCst);
            if sso_session_id == SSO_DOWN {
                return Err(SsoUnavailable("simulovaný výpadok".to_string()));
            }
            Ok(self.identities.get(sso_session_id).cloned())
        })
    }
}

pub struct TestApp {
    pub router: Router,
    pub state: AppState,
    pub store: Arc<InMemorySharedSessionStore>,
    pub sso: Arc<FakeSso>,
}

/// Dnešok v testoch.
pub fn today() -> NaiveDate {
    NaiveDate::from_ymd_opt(2026, 10, 14).expect("dátum")
}

/// Appka so všetkými závislosťami v pamäti.
pub fn test_app() -> TestApp {
    let store = Arc::new(InMemorySharedSessionStore::default());
    let sso = Arc::new(FakeSso::demo());
    let clock: Arc<dyn Clock> = Arc::new(FixedClock(today().and_hms_opt(10, 0, 0).expect("čas")));
    let state = AppState {
        config: Arc::new(Config::from_lookup(|_| None)),
        sso: sso.clone(),
        store: store.clone(),
        local_sessions: Arc::new(LocalSessions::default()),
        acl: Arc::new(InMemoryAclService::default()),
        blacklist: Arc::new(InMemoryBlacklistService::new(clock.clone())),
        messages: Arc::new(Messages::load()),
        clock,
    };
    TestApp { router: app(state.clone()), state, store, sso }
}

pub struct TestResponse {
    pub status: StatusCode,
    pub headers: HeaderMap,
    pub body: String,
}

impl TestResponse {
    /// Hlavička Location (redirect).
    pub fn location(&self) -> &str {
        self.headers.get(LOCATION).and_then(|v| v.to_str().ok()).unwrap_or("")
    }

    /// Celá hlavička Set-Cookie pre danú cookie.
    pub fn set_cookie(&self, name: &str) -> Option<String> {
        self.headers.get_all(SET_COOKIE).iter()
            .filter_map(|v| v.to_str().ok())
            .find(|v| v.starts_with(&format!("{name}=")))
            .map(String::from)
    }
}

/// „Prehliadač“: posiela SSO_ID a pamätá si CRM_RS_SESSION z odpovedí.
pub struct Browser {
    router: Router,
    sso: Option<String>,
    local: Option<String>,
}

impl Browser {
    pub fn new(router: &Router, sso: Option<&str>) -> Self {
        Self { router: router.clone(), sso: sso.map(String::from), local: None }
    }

    pub async fn get(&mut self, path: &str) -> TestResponse {
        self.send(Method::GET, path, &[], Body::empty()).await
    }

    pub async fn post(&mut self, path: &str, form: &str) -> TestResponse {
        let headers = [("content-type", "application/x-www-form-urlencoded")];
        self.send(Method::POST, path, &headers, Body::from(form.to_string())).await
    }

    pub async fn ajax(&mut self, method: Method, path: &str) -> TestResponse {
        self.send(method, path, &[("x-requested-with", "XMLHttpRequest")], Body::empty()).await
    }

    async fn send(&mut self, method: Method, path: &str, headers: &[(&str, &str)], body: Body) -> TestResponse {
        let mut builder = Request::builder().method(method).uri(path);
        let cookies: Vec<String> = [self.sso.as_ref().map(|v| format!("SSO_ID={v}")),
            self.local.as_ref().map(|v| format!("CRM_RS_SESSION={v}"))].into_iter().flatten().collect();
        if !cookies.is_empty() {
            builder = builder.header(COOKIE, cookies.join("; "));
        }
        for (name, value) in headers {
            builder = builder.header(*name, *value);
        }
        let response = self.router.clone().oneshot(builder.body(body).expect("request")).await.expect("odpoveď");
        let status = response.status();
        let headers = response.headers().clone();
        let bytes = response.into_body().collect().await.expect("telo").to_bytes();
        let result = TestResponse { status, headers, body: String::from_utf8_lossy(&bytes).into_owned() };
        if let Some(cookie) = result.set_cookie("CRM_RS_SESSION") {
            self.local = cookie.split(';').next().and_then(|c| c.split_once('=')).map(|(_, v)| v.to_string());
        }
        result
    }
}
