//! Zapojenie appky (Java: Spring kontext + `WebConfig`) a Router. Cesty sú plné (`/crm-rs/…`) – kontext je
//! súčasť URL ako `server.servlet.context-path`, nginx ho neodstraňuje.

use std::path::PathBuf;
use std::sync::Arc;

use axum::Router;
use axum::extract::State;
use axum::http::HeaderMap;
use axum::response::{IntoResponse, Response};
use axum::routing::get;
use tower_http::services::ServeDir;

use crate::CONTEXT_PATH;
use crate::acl::{AclService, InMemoryAclService};
use crate::blacklist::service::{BlacklistService, InMemoryBlacklistService};
use crate::clock::{Clock, SystemClock};
use crate::config::Config;
use crate::session::identity::SsoApi;
use crate::session::local::LocalSessions;
use crate::session::sso_client::SsoClient;
use crate::session::store::{RedisSharedSessionStore, SharedSessionStore};
use crate::web::errors::{AppError, redirect};
use crate::web::layout::Page;
use crate::web::messages::Messages;
use crate::web::portlet::resolve_identity;
use crate::web::tags::portlet_path;

/// Závislosti zdieľané všetkými requestmi (traity – v testoch náhrady bez siete).
#[derive(Clone)]
pub struct AppState {
    pub config: Arc<Config>,
    pub sso: Arc<dyn SsoApi>,
    pub store: Arc<dyn SharedSessionStore>,
    pub local_sessions: Arc<LocalSessions>,
    pub acl: Arc<dyn AclService>,
    pub blacklist: Arc<dyn BlacklistService>,
    pub messages: Arc<Messages>,
    pub clock: Arc<dyn Clock>,
}

impl AppState {
    /// Produkčné zapojenie: SSO cez HTTP, Redis, in-memory backend a ACL (ako Java demo).
    pub fn production(config: Config) -> Result<Self, String> {
        let clock: Arc<dyn Clock> = Arc::new(SystemClock);
        Ok(Self {
            sso: Arc::new(SsoClient::new(&config.sso_api_url).map_err(|e| e.to_string())?),
            store: Arc::new(RedisSharedSessionStore::new(&config.redis_url).map_err(|e| e.to_string())?),
            local_sessions: Arc::new(LocalSessions::default()),
            acl: Arc::new(InMemoryAclService::default()),
            blacklist: Arc::new(InMemoryBlacklistService::new(clock.clone())),
            messages: Arc::new(Messages::load()),
            clock,
            config: Arc::new(config),
        })
    }
}

/// Router celej appky.
pub fn app(state: AppState) -> Router {
    let static_dir = PathBuf::from(&state.config.static_dir);
    Router::new()
        .route(CONTEXT_PATH, get(home))
        .route(&format!("{CONTEXT_PATH}/"), get(home))
        .route(&portlet_path(""), get(home))
        .nest_service(&format!("{CONTEXT_PATH}/css"), ServeDir::new(static_dir.join("css")))
        .nest_service(&format!("{CONTEXT_PATH}/js"), ServeDir::new(static_dir.join("js")))
        .merge(crate::blacklist::search::routes())
        .merge(crate::blacklist::detail::routes())
        .merge(crate::blacklist::create::routes())
        .fallback(fallback)
        .with_state(state)
}

/// Vstupné body vedú na vyhľadávanie (Java `HomeController`, bez prihlásenia).
async fn home() -> Response {
    redirect(&portlet_path("/search"))
}

/// Neznáma cesta: vlastná 404 s layoutom (Java Boot error page cez `LayoutModelInterceptor`).
async fn fallback(State(state): State<AppState>, headers: HeaderMap) -> Response {
    // SSO chyba na neznámej ceste nie je podstatná – stránka sa vykreslí bez identity.
    let identity = resolve_identity(&headers, &state).await.unwrap_or(None);
    AppError::NotFound(Page::build(&state, identity.as_ref()).await).into_response()
}
