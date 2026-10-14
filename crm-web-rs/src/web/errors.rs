//! Chyby a odpovede (Java `GlobalExceptionHandler` + chybové JSP). Chyba nesie už pripravenú stránku (`Page`),
//! lebo `IntoResponse` nemá prístup k stavu appky.

use askama::Template;
use axum::http::header::{LOCATION, SET_COOKIE};
use axum::http::{HeaderValue, StatusCode};
use axum::response::{Html, IntoResponse, Response};

use crate::acl::Privilege;
use crate::session::local::set_cookie_value;
use crate::web::layout::{LayoutModel, Page};
use crate::web::tags::Tags;

/// Obal chybovej odpovede: AJAX dostane fragment bez layoutu, stránka celý layout.
pub enum Shell {
    Fragment(Tags),
    Page(Page),
}

/// Chyba requestu.
pub enum AppError {
    /// Bez SSO identity: stránka → SSO login, AJAX → 401.
    LoginRequired { ajax: bool, login_url: String },
    SsoUnavailable(Page),
    AclDenied { privilege: Privilege, shell: Shell },
    NotFound(Page),
    /// Zlý parameter (Java 400 cez Boot error page); `None` = AJAX.
    BadRequest(Option<Page>),
    Internal,
}

#[derive(Template)]
#[template(path = "error/403.html")]
struct ForbiddenPage {
    tags: Tags,
    layout: LayoutModel,
    title_key: &'static str,
    privilege_filter: Option<&'static str>,
    privilege: &'static str,
}

#[derive(Template)]
#[template(path = "error/403-fragment.html")]
struct ForbiddenFragment {
    tags: Tags,
    privilege: &'static str,
}

#[derive(Template)]
#[template(path = "error/404.html")]
struct NotFoundPage {
    tags: Tags,
    layout: LayoutModel,
    title_key: &'static str,
    privilege_filter: Option<&'static str>,
}

#[derive(Template)]
#[template(path = "error/sso.html")]
struct SsoErrorPage {
    tags: Tags,
    layout: LayoutModel,
    title_key: &'static str,
    privilege_filter: Option<&'static str>,
}

#[derive(Template)]
#[template(path = "error/error.html")]
struct GenericErrorPage {
    tags: Tags,
    layout: LayoutModel,
    title_key: &'static str,
    privilege_filter: Option<&'static str>,
    status: u16,
    error: &'static str,
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        match self {
            AppError::LoginRequired { ajax: true, .. } => StatusCode::UNAUTHORIZED.into_response(),
            AppError::LoginRequired { ajax: false, login_url } => redirect(&login_url),
            AppError::SsoUnavailable(Page { tags, layout }) => with_status(StatusCode::SERVICE_UNAVAILABLE,
                html(&SsoErrorPage { tags, layout, title_key: "error.generic.title", privilege_filter: None })),
            AppError::AclDenied { privilege, shell: Shell::Fragment(tags) } =>
                with_status(StatusCode::FORBIDDEN, html(&ForbiddenFragment { tags, privilege: privilege.name() })),
            AppError::AclDenied { privilege, shell: Shell::Page(Page { tags, layout }) } => with_status(StatusCode::FORBIDDEN,
                html(&ForbiddenPage { tags, layout, title_key: "error.403.title", privilege_filter: None, privilege: privilege.name() })),
            AppError::NotFound(Page { tags, layout }) => with_status(StatusCode::NOT_FOUND,
                html(&NotFoundPage { tags, layout, title_key: "error.404.title", privilege_filter: None })),
            AppError::BadRequest(Some(Page { tags, layout })) => with_status(StatusCode::BAD_REQUEST,
                html(&GenericErrorPage { tags, layout, title_key: "error.generic.title", privilege_filter: None,
                    status: 400, error: "Bad Request" })),
            AppError::BadRequest(None) => StatusCode::BAD_REQUEST.into_response(),
            AppError::Internal => internal_error(),
        }
    }
}

fn internal_error() -> Response {
    (StatusCode::INTERNAL_SERVER_ERROR, "Interná chyba – podrobnosti sú v logu.").into_response()
}

/// Vykreslí šablónu ako HTML; chyba šablóny = 500 + log.
pub fn html<T: Template>(template: &T) -> Response {
    match template.render() {
        Ok(body) => Html(body).into_response(),
        Err(e) => {
            tracing::error!("Vykreslenie šablóny zlyhalo: {e}");
            internal_error()
        }
    }
}

/// Nastaví stav odpovede, ak sa vykreslenie podarilo (500 z `html` ostáva).
pub fn with_status(status: StatusCode, mut response: Response) -> Response {
    if response.status().is_success() {
        *response.status_mut() = status;
    }
    response
}

/// 302 ako Spring `redirect:` (axum `Redirect::to` dáva 303).
pub fn redirect(location: &str) -> Response {
    (StatusCode::FOUND, [(LOCATION, location.to_string())]).into_response()
}

/// Pridá Set-Cookie novej lokálnej session.
pub fn with_cookie(mut response: Response, new_session_id: Option<String>) -> Response {
    if let Some(value) = new_session_id.and_then(|id| HeaderValue::from_str(&set_cookie_value(&id)).ok()) {
        response.headers_mut().append(SET_COOKIE, value);
    }
    response
}
