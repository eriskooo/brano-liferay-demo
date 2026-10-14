//! Vyhľadávanie (Java `BlacklistSearchController`): render = GET, action = POST + redirect (PRG).
//! Kritériá žijú v lokálnej session ako `@SessionAttributes("searchCriteria")`.

use askama::Template;
use axum::response::Response;
use axum::routing::{get, post};
use axum::{Form, Router};

use crate::blacklist::forms::SearchForm;
use crate::blacklist::model::{BlacklistRecord, BlacklistState};
use crate::web::app::AppState;
use crate::web::errors::{html, with_cookie};
use crate::web::layout::LayoutModel;
use crate::web::portlet::{Ctx, Require, View};
use crate::web::tags::{FieldErrors, Tags, portlet_path};

const SEARCH_CRITERIA: &str = "searchCriteria";

/// Routy vyhľadávania.
pub fn routes() -> Router<AppState> {
    Router::new()
        .route(&portlet_path("/search"), get(search_page))
        .route(&portlet_path("/searchAction"), post(search_action))
        .route(&portlet_path("/searchresult"), get(search_result))
        .route(&portlet_path("/newSearch"), get(new_search))
}

#[derive(Template)]
#[template(path = "blacklist/search.html")]
struct SearchPage {
    tags: Tags,
    layout: LayoutModel,
    title_key: &'static str,
    privilege_filter: Option<&'static str>,
    form: SearchForm,
    errors: FieldErrors,
    states: &'static [BlacklistState],
}

#[derive(Template)]
#[template(path = "blacklist/searchresult.html")]
struct SearchResultPage {
    tags: Tags,
    layout: LayoutModel,
    title_key: &'static str,
    privilege_filter: Option<&'static str>,
    results: Vec<BlacklistRecord>,
}

async fn search_page(Require(ctx, _): Require<View>) -> Response {
    let form = ctx.local_get::<SearchForm>(SEARCH_CRITERIA).unwrap_or_default();
    render_search(&ctx, form, FieldErrors::default()).await
}

async fn search_action(Require(ctx, _): Require<View>, Form(posted): Form<SearchForm>) -> Response {
    let form = ctx.local_get::<SearchForm>(SEARCH_CRITERIA).unwrap_or_default().merge(posted);
    // Ako @SessionAttributes: naviazané kritériá sa uložia aj pri validačnej chybe.
    let new_session = ctx.local_put(SEARCH_CRITERIA, form.clone());
    let response = match form.validate() {
        // Validačná chyba sa vykreslí hneď (bez redirectu), aby ostali chyby pri poliach – ako render po action.
        Err(violations) => {
            let errors = FieldErrors::resolve(&violations, ctx.tags.messages());
            render_search(&ctx, form, errors).await
        }
        Ok(criteria) => {
            // Zdieľaná session: posledné kritériá vidí aj Java appka a stará appka.
            ctx.session.set(&form.to_shared()).await;
            if ctx.state.blacklist.search(&criteria).is_empty() {
                ctx.session.push_info("blacklist.search.noResult").await;
                ctx.redirect("/search")
            } else {
                ctx.redirect("/searchresult")
            }
        }
    };
    with_cookie(response, new_session)
}

async fn search_result(Require(ctx, _): Require<View>) -> Response {
    let form = ctx.local_get::<SearchForm>(SEARCH_CRITERIA).unwrap_or_default();
    // Hľadá sa znova (ako legacy render fáza) – výsledky sa v session nedržia.
    let results = ctx.state.blacklist.search(&form.criteria());
    let page = ctx.page().await;
    html(&SearchResultPage {
        tags: page.tags,
        layout: page.layout,
        title_key: "blacklist.result.title",
        privilege_filter: Some("BLACKLIST_VIEW"),
        results,
    })
}

/// Legacy „vyčistiť polia“ bola action fáza; je to len navigácia, preto GET.
async fn new_search(Require(ctx, _): Require<View>) -> Response {
    ctx.local_remove(SEARCH_CRITERIA);
    ctx.redirect("/search")
}

async fn render_search(ctx: &Ctx, form: SearchForm, errors: FieldErrors) -> Response {
    let page = ctx.page().await;
    html(&SearchPage {
        tags: page.tags,
        layout: page.layout,
        title_key: "blacklist.search.title",
        privilege_filter: Some("BLACKLIST_VIEW"),
        form,
        errors,
        states: BlacklistState::ALL,
    })
}
