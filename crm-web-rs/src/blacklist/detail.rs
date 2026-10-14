//! Detail záznamu (Java `BlacklistDetailController`): render `detail`, action `changeAction`/`addNoteAction`
//! a resource `removeIdentAction` (AJAX, vracia HTML fragment – JS na stránke sa nemení).

use askama::Template;
use axum::extract::Query;
use axum::response::Response;
use axum::routing::{get, post};
use axum::{Form, Router};
use serde::Deserialize;

use crate::blacklist::forms::{ChangeForm, NoteForm};
use crate::blacklist::model::{BlacklistRecord, BlacklistState, Identifier};
use crate::blacklist::service::BlacklistError;
use crate::session::keys::LastViewedRecord;
use crate::web::app::AppState;
use crate::web::errors::{AppError, html};
use crate::web::layout::LayoutModel;
use crate::web::portlet::{Ctx, Modify, Require, View};
use crate::web::tags::{FieldErrors, Tags, portlet_path};

/// Routy detailu.
pub fn routes() -> Router<AppState> {
    Router::new()
        .route(&portlet_path("/detail"), get(detail))
        .route(&portlet_path("/changeAction"), post(change))
        .route(&portlet_path("/addNoteAction"), post(add_note))
        .route(&portlet_path("/resource/removeIdentAction"), post(remove_identifier))
}

/// Parametre v URL (detailId ide v URL akcie ako v Jave). Reťazce – zlé číslo rieši `Ctx::require_id` (400/404).
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DetailParams {
    detail_id: Option<String>,
    ident_id: Option<String>,
}

#[derive(Template)]
#[template(path = "blacklist/detail.html")]
struct DetailPage {
    tags: Tags,
    layout: LayoutModel,
    title_key: &'static str,
    privilege_filter: Option<&'static str>,
    record: BlacklistRecord,
    identifiers: Vec<Identifier>,
    detail_id: u64,
    error_key: &'static str,
    states: &'static [BlacklistState],
    change_form: ChangeForm,
    change_errors: FieldErrors,
    note_form: NoteForm,
    note_errors: FieldErrors,
}

#[derive(Template)]
#[template(path = "blacklist/fragments/identifiers.html")]
struct IdentifiersFragment {
    tags: Tags,
    identifiers: Vec<Identifier>,
    detail_id: u64,
    error_key: &'static str,
}

/// Formuláre detailu; pri validačnej chybe ostane odoslaný formulár s chybami (Java: model ho neprepíše).
struct DetailForms {
    change: ChangeForm,
    change_errors: FieldErrors,
    note: NoteForm,
    note_errors: FieldErrors,
}

impl DetailForms {
    fn for_record(record: &BlacklistRecord) -> Self {
        Self {
            change: ChangeForm::from_record(record),
            change_errors: FieldErrors::default(),
            note: NoteForm::default(),
            note_errors: FieldErrors::default(),
        }
    }
}

async fn detail(Require(ctx, _): Require<View>, Query(params): Query<DetailParams>) -> Result<Response, AppError> {
    let id = ctx.require_id(&params.detail_id, "záznam blacklistu").await?;
    let record = load(&ctx, id).await?;
    // Zdieľaná session: hlavička Java appky aj Liferay ukáže, s kým operátor naposledy pracoval.
    ctx.session.set(&LastViewedRecord { id, full_name: record.full_name() }).await;
    let forms = DetailForms::for_record(&record);
    Ok(render_detail(&ctx, record, forms).await)
}

async fn change(Require(ctx, _): Require<Modify>, Query(params): Query<DetailParams>, Form(form): Form<ChangeForm>)
    -> Result<Response, AppError> {
    let id = ctx.require_id(&params.detail_id, "záznam blacklistu").await?;
    match form.validate() {
        Err(violations) => {
            let record = load(&ctx, id).await?;
            let change_errors = FieldErrors::resolve(&violations, ctx.tags.messages());
            let forms = DetailForms { change: form, change_errors, ..DetailForms::for_record(&record) };
            Ok(render_detail(&ctx, record, forms).await)
        }
        Ok((state, valid_to)) => {
            if let Err(e) = ctx.state.blacklist.change(id, state, valid_to) {
                return Err(fail(&ctx, e).await);
            }
            ctx.session.push_info("blacklist.detail.changed").await;
            Ok(ctx.redirect(&format!("/detail?detailId={id}")))
        }
    }
}

async fn add_note(Require(ctx, _): Require<Modify>, Query(params): Query<DetailParams>, Form(form): Form<NoteForm>)
    -> Result<Response, AppError> {
    let id = ctx.require_id(&params.detail_id, "záznam blacklistu").await?;
    match form.validate() {
        Err(violations) => {
            let record = load(&ctx, id).await?;
            let note_errors = FieldErrors::resolve(&violations, ctx.tags.messages());
            let forms = DetailForms { note: form, note_errors, ..DetailForms::for_record(&record) };
            Ok(render_detail(&ctx, record, forms).await)
        }
        Ok(text) => {
            if let Err(e) = ctx.state.blacklist.add_note(id, &text, &ctx.identity.operator.display_name) {
                return Err(fail(&ctx, e).await);
            }
            ctx.session.push_info("blacklist.detail.noteAdded").await;
            Ok(ctx.redirect(&format!("/detail?detailId={id}")))
        }
    }
}

/// Resource fáza → AJAX endpoint, vracia HTML fragment ako legacy.
async fn remove_identifier(Require(ctx, _): Require<Modify>, Query(params): Query<DetailParams>)
    -> Result<Response, AppError> {
    let id = ctx.require_id(&params.detail_id, "záznam blacklistu").await?;
    let identifier_id = ctx.require_id(&params.ident_id, "identifikátor").await?;
    let (identifiers, error_key) = match ctx.state.blacklist.remove_identifier(id, identifier_id) {
        Ok(remaining) => (remaining, ""),
        Err(BlacklistError::Rule(key)) => (load(&ctx, id).await?.identifiers, key),
        Err(e) => return Err(fail(&ctx, e).await),
    };
    Ok(html(&IdentifiersFragment { tags: ctx.tags.clone(), identifiers, detail_id: id, error_key }))
}

async fn render_detail(ctx: &Ctx, record: BlacklistRecord, forms: DetailForms) -> Response {
    let page = ctx.page().await;
    html(&DetailPage {
        tags: page.tags,
        layout: page.layout,
        title_key: "blacklist.detail.title",
        privilege_filter: Some("BLACKLIST_VIEW"),
        identifiers: record.identifiers.clone(),
        detail_id: record.id,
        record,
        error_key: "",
        states: BlacklistState::ALL,
        change_form: forms.change,
        change_errors: forms.change_errors,
        note_form: forms.note,
        note_errors: forms.note_errors,
    })
}

async fn load(ctx: &Ctx, id: u64) -> Result<BlacklistRecord, AppError> {
    match ctx.state.blacklist.find_by_id(id) {
        Some(record) => Ok(record),
        None => Err(ctx.not_found(&format!("záznam blacklistu {id}")).await),
    }
}

async fn fail(ctx: &Ctx, error: BlacklistError) -> AppError {
    match error {
        BlacklistError::NotFound(what) => ctx.not_found(&what).await,
        BlacklistError::Rule(key) => {
            tracing::error!("Neošetrené pravidlo blacklistu: {key}");
            AppError::Internal
        }
    }
}
