//! Nový záznam (Java `BlacklistCreateController`): render `create`, action `createAction`,
//! resource `refreshCreateConditionTypes`. Celý „portlet“ vyžaduje BLACKLIST_MODIFY.

use askama::Template;
use axum::extract::Query;
use axum::response::Response;
use axum::routing::{get, post};
use axum::{Form, Router};
use serde::Deserialize;

use crate::blacklist::forms::{CreateForm, parse_enum};
use crate::blacklist::model::{ConditionType, IdentifierType, StateReason};
use crate::web::app::AppState;
use crate::web::errors::{AppError, html};
use crate::web::layout::LayoutModel;
use crate::web::portlet::{Ctx, Modify, Require};
use crate::web::tags::{FieldErrors, Tags, portlet_path};

/// Routy nového záznamu.
pub fn routes() -> Router<AppState> {
    Router::new()
        .route(&portlet_path("/create"), get(create_page))
        .route(&portlet_path("/resource/refreshCreateConditionTypes"), get(refresh_condition_types))
        .route(&portlet_path("/createAction"), post(create_action))
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct RefreshParams {
    state_reason: Option<String>,
}

#[derive(Template)]
#[template(path = "blacklist/create.html")]
struct CreatePage {
    tags: Tags,
    layout: LayoutModel,
    title_key: &'static str,
    privilege_filter: Option<&'static str>,
    form: CreateForm,
    errors: FieldErrors,
    state_reasons: &'static [StateReason],
    identifier_types: &'static [IdentifierType],
    condition_types: Vec<ConditionType>,
    selected_condition: String,
}

#[derive(Template)]
#[template(path = "blacklist/fragments/conditionTypes.html")]
struct ConditionTypesFragment {
    tags: Tags,
    condition_types: Vec<ConditionType>,
    selected_condition: String,
}

async fn create_page(Require(ctx, _): Require<Modify>) -> Response {
    let form = CreateForm::initial(ctx.state.clock.today());
    render_create(&ctx, form, FieldErrors::default()).await
}

/// Resource fáza → AJAX: po zmene dôvodu HTML fragment so zoznamom povolených podmienok.
async fn refresh_condition_types(Require(ctx, _): Require<Modify>, Query(params): Query<RefreshParams>)
    -> Result<Response, AppError> {
    let Ok(reason) = parse_enum(&params.state_reason, StateReason::from_name) else {
        return Err(ctx.bad_request().await);
    };
    Ok(html(&ConditionTypesFragment {
        tags: ctx.tags.clone(),
        condition_types: ctx.state.blacklist.allowed_condition_types(reason),
        selected_condition: String::new(),
    }))
}

async fn create_action(Require(ctx, _): Require<Modify>, Form(form): Form<CreateForm>) -> Response {
    let today = ctx.state.clock.today();
    let form = form.with_default_valid_from(today);
    let service = ctx.state.blacklist.clone();
    // Povolené kombinácie dôvod/podmienka určuje backend – preto pravidlo dostane funkciu služby.
    match form.validate(today, |reason| service.allowed_condition_types(reason)) {
        Err(violations) => {
            let errors = FieldErrors::resolve(&violations, ctx.tags.messages());
            render_create(&ctx, form, errors).await
        }
        Ok(command) => {
            let created = service.create(command);
            ctx.session.push_info("blacklist.create.created").await;
            ctx.redirect(&format!("/detail?detailId={}", created.id))
        }
    }
}

async fn render_create(ctx: &Ctx, form: CreateForm, errors: FieldErrors) -> Response {
    let condition_types = ctx.state.blacklist.allowed_condition_types(form.state_reason());
    let selected_condition = form.v("conditionType").to_string();
    let page = ctx.page().await;
    html(&CreatePage {
        tags: page.tags,
        layout: page.layout,
        title_key: "blacklist.create.title",
        privilege_filter: Some("BLACKLIST_MODIFY"),
        form,
        errors,
        state_reasons: StateReason::ALL,
        identifier_types: IdentifierType::ALL,
        condition_types,
        selected_condition,
    })
}
