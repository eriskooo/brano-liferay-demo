//! „Portlet“ = skupina handlerov so spoločným base path. Extraktory robia to, čo Java `LegacyPortletInterceptor`:
//! bez identity zo SSO nepustia ďalej (stránka → SSO login, AJAX → 401) a skontrolujú privilégium. Privilégium je
//! typ v signatúre handlera (`Require<Modify>`), takže sa nedá zabudnúť ako anotácia.

use std::marker::PhantomData;

use axum::extract::FromRequestParts;
use axum::http::request::Parts;
use axum::http::{HeaderMap, Method};
use axum::response::Response;

use crate::CONTEXT_PATH;
use crate::acl::Privilege;
use crate::session::identity::{SSO_COOKIE, SsoIdentity, SsoUnavailable, is_valid_sso_id};
use crate::session::local;
use crate::session::shared::CrmSession;
use crate::web::app::AppState;
use crate::web::errors::{AppError, Shell, redirect};
use crate::web::layout::Page;
use crate::web::requests::{cookie, is_ajax};
use crate::web::tags::{PORTLET_BASE, Tags, encode, portlet_path};

/// Kontext prihláseného operátora pre jeden request (identita sa zo SSO pýta raz).
pub struct Ctx {
    pub state: AppState,
    pub identity: SsoIdentity,
    pub session: CrmSession,
    pub local_session_id: Option<String>,
    pub ajax: bool,
    pub tags: Tags,
}

/// Identita podľa cookie SSO_ID; neplatný tvar = neprihlásený bez volania SSO.
pub async fn resolve_identity(headers: &HeaderMap, state: &AppState) -> Result<Option<SsoIdentity>, SsoUnavailable> {
    match cookie(headers, SSO_COOKIE).filter(|value| is_valid_sso_id(value)) {
        Some(sso_id) => state.sso.fetch(&sso_id).await,
        None => Ok(None),
    }
}

/// SSO login s návratom na `return_path` (Java `SsoIdentityResolver.loginUrl`).
pub fn login_url(sso_login_url: &str, return_path: &str) -> String {
    format!("{sso_login_url}?returnUrl={}", encode(return_path))
}

/// Kam sa vrátiť po prihlásení: GET na tú istú adresu, inak na stránku portletu (POST URL by po návrate dala 405).
fn return_path(parts: &Parts) -> String {
    if parts.method != Method::GET {
        return format!("{CONTEXT_PATH}{PORTLET_BASE}");
    }
    parts.uri.path_and_query().map(|pq| pq.as_str().to_string()).unwrap_or_else(|| parts.uri.path().to_string())
}

impl FromRequestParts<AppState> for Ctx {
    type Rejection = AppError;

    async fn from_request_parts(parts: &mut Parts, state: &AppState) -> Result<Self, AppError> {
        let ajax = is_ajax(&parts.headers);
        let identity = match resolve_identity(&parts.headers, state).await {
            Ok(identity) => identity,
            Err(e) => {
                tracing::error!("{e}");
                return Err(AppError::SsoUnavailable(Page::build(state, None).await));
            }
        };
        let Some(identity) = identity else {
            let login_url = login_url(&state.config.sso_login_url, &return_path(parts));
            return Err(AppError::LoginRequired { ajax, login_url });
        };
        Ok(Ctx {
            session: CrmSession::new(Some(identity.sso_session_id.clone()), state.store.clone()),
            local_session_id: cookie(&parts.headers, local::COOKIE),
            tags: Tags::new(state.messages.clone(), state.acl.privileges_of(&identity.operator)),
            identity,
            ajax,
            state: state.clone(),
        })
    }
}

/// Privilégium vyžadované handlerom (Java `@RequiresPrivilege`).
pub trait RequiredPrivilege {
    const PRIVILEGE: Privilege;
}

/// Prezeranie blacklistu.
pub struct View;
/// Zmeny v blackliste.
pub struct Modify;

impl RequiredPrivilege for View {
    const PRIVILEGE: Privilege = Privilege::BlacklistView;
}

impl RequiredPrivilege for Modify {
    const PRIVILEGE: Privilege = Privilege::BlacklistModify;
}

/// Prihlásený operátor s privilégiom `P`; inak 403 (stránka alebo AJAX fragment).
pub struct Require<P>(pub Ctx, pub PhantomData<P>);

impl<P: RequiredPrivilege + Send + Sync> FromRequestParts<AppState> for Require<P> {
    type Rejection = AppError;

    async fn from_request_parts(parts: &mut Parts, state: &AppState) -> Result<Self, AppError> {
        let ctx = Ctx::from_request_parts(parts, state).await?;
        if !ctx.tags.has(P::PRIVILEGE) {
            let shell = if ctx.ajax { Shell::Fragment(ctx.tags.clone()) } else { Shell::Page(ctx.page().await) };
            return Err(AppError::AclDenied { privilege: P::PRIVILEGE, shell });
        }
        Ok(Require(ctx, PhantomData))
    }
}

impl Ctx {
    /// Tagy + hlavička pre vykreslenie stránky (vyberie hlášky).
    pub async fn page(&self) -> Page {
        Page::build(&self.state, Some(&self.identity)).await
    }

    /// PRG redirect v rámci portletu (`redirect:/blacklist/…`).
    pub fn redirect(&self, path: &str) -> Response {
        redirect(&portlet_path(path))
    }

    /// Hodnota z lokálnej session.
    pub fn local_get<T: Clone + 'static>(&self, key: &'static str) -> Option<T> {
        self.state.local_sessions.get(self.local_session_id.as_deref(), key)
    }

    /// Zápis do lokálnej session; `Some(id)` = nová session, odpoveď musí poslať Set-Cookie.
    pub fn local_put<T: Send + Sync + 'static>(&self, key: &'static str, value: T) -> Option<String> {
        self.state.local_sessions.put(self.local_session_id.as_deref(), key, value)
    }

    /// Odstránenie z lokálnej session.
    pub fn local_remove(&self, key: &'static str) {
        self.state.local_sessions.remove(self.local_session_id.as_deref(), key);
    }

    /// Povinné číselné ID z parametra; chýba/nie je číslo → 400, záporné → 404 (Java `long` ho prijme, záznam neexistuje).
    pub async fn require_id(&self, raw: &Option<String>, what: &str) -> Result<u64, AppError> {
        let Some(id) = raw.as_deref().map(str::trim).and_then(|v| v.parse::<i64>().ok()) else {
            return Err(self.bad_request().await);
        };
        match u64::try_from(id) {
            Ok(id) => Ok(id),
            Err(_) => Err(self.not_found(&format!("{what} {id}")).await),
        }
    }

    /// 404 stránka (aj pre AJAX – Java vracia celú chybovú stránku).
    pub async fn not_found(&self, what: &str) -> AppError {
        tracing::debug!("Nenájdené: {what}");
        AppError::NotFound(self.page().await)
    }

    /// 400; AJAX bez layoutu, aby sa nevybrali hlášky.
    pub async fn bad_request(&self) -> AppError {
        if self.ajax { AppError::BadRequest(None) } else { AppError::BadRequest(Some(self.page().await)) }
    }
}
