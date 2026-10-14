//! Dáta hlavičky (Java `LayoutModel` – náhrada portletu „header“ z Liferay theme). Ploché reťazce a bool,
//! aby šablóna nepotrebovala `match` nad Option.

use crate::session::identity::SsoIdentity;
use crate::session::keys::{LastViewedRecord, UiMessage};
use crate::session::shared::CrmSession;
use crate::web::app::AppState;
use crate::web::tags::Tags;

/// Hlavička stránky.
#[derive(Debug, Clone, Default)]
pub struct LayoutModel {
    pub logged_in: bool,
    pub operator_name: String,
    pub customer_name: String,
    pub customer_id: String,
    pub has_last_viewed: bool,
    pub last_viewed_name: String,
    pub messages: Vec<UiMessage>,
    pub legacy_flag: String,
    pub sso_id_display: String,
}

impl LayoutModel {
    /// Hlášky sa tu vyberú (zobrazia sa raz) – volať len pri skutočnom vykreslení stránky, nie pri redirecte/AJAX.
    pub async fn build(identity: Option<&SsoIdentity>, session: &CrmSession) -> Self {
        let messages = session.pop_messages().await;
        let last_viewed = session.get::<LastViewedRecord>().await;
        let text = |f: fn(&SsoIdentity) -> &String| identity.map(|i| f(i).clone()).unwrap_or_default();
        Self {
            logged_in: identity.is_some(),
            operator_name: text(|i| &i.operator.display_name),
            customer_name: text(|i| &i.customer.display_name),
            customer_id: text(|i| &i.customer.customer_id),
            has_last_viewed: last_viewed.is_some(),
            last_viewed_name: last_viewed.map(|r| r.full_name).unwrap_or_default(),
            messages,
            legacy_flag: session.legacy_read_source().await.unwrap_or_else(|| "PORTLET".to_string()),
            sso_id_display: identity.map(|i| i.sso_session_id.clone()).unwrap_or_else(|| "-".to_string()),
        }
    }
}

/// Všetko, čo stránka s layoutom potrebuje okrem vlastných dát.
pub struct Page {
    pub tags: Tags,
    pub layout: LayoutModel,
}

impl Page {
    /// Tagy podľa privilégií operátora + hlavička zo zdieľanej session.
    pub async fn build(state: &AppState, identity: Option<&SsoIdentity>) -> Self {
        let privileges = identity.map(|i| state.acl.privileges_of(&i.operator)).unwrap_or_default();
        let session = CrmSession::new(identity.map(|i| i.sso_session_id.clone()), state.store.clone());
        Self {
            tags: Tags::new(state.messages.clone(), privileges),
            layout: LayoutModel::build(identity, &session).await,
        }
    }
}
