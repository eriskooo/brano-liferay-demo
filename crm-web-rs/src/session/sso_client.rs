//! REST klient SSO portálu. Legacy mal „provided“ API JAR v classloaderi portálu; Java aj Rust appka volajú
//! ten istý endpoint priamo. Krátke timeouty – SSO sa pýta pri každom requeste.

use std::time::Duration;

use reqwest::StatusCode;
use serde::Deserialize;

use crate::BoxFuture;
use crate::session::identity::{CustomerContext, OperatorUser, SsoApi, SsoIdentity, SsoUnavailable};

/// HTTP klient na `{api}/api/sessions/{id}`.
pub struct SsoClient {
    http: reqwest::Client,
    api_url: String,
}

/// Tvar odpovede sso-mocku.
#[derive(Deserialize)]
struct SessionView {
    operator: OperatorUser,
    customer: CustomerContext,
}

impl SsoClient {
    /// Klient s timeoutmi ako Java (1 s spojenie, 2 s celý request).
    pub fn new(api_url: &str) -> Result<Self, SsoUnavailable> {
        let http = reqwest::Client::builder()
            .connect_timeout(Duration::from_secs(1))
            .timeout(Duration::from_secs(2))
            .build()
            .map_err(|e| SsoUnavailable(e.to_string()))?;
        Ok(Self { http, api_url: api_url.trim_end_matches('/').to_string() })
    }

    async fn fetch_identity(&self, sso_session_id: &str) -> Result<Option<SsoIdentity>, SsoUnavailable> {
        // ID je overené is_valid_sso_id – do cesty URL sa vloží bez kódovania.
        let url = format!("{}/api/sessions/{sso_session_id}", self.api_url);
        let response = self.http.get(url).send().await.map_err(|e| SsoUnavailable(e.to_string()))?;
        if response.status() == StatusCode::NOT_FOUND {
            return Ok(None);
        }
        if !response.status().is_success() {
            return Err(SsoUnavailable(format!("SSO portál vrátil {}", response.status())));
        }
        let view: SessionView = response.json().await.map_err(|e| SsoUnavailable(e.to_string()))?;
        Ok(Some(SsoIdentity {
            sso_session_id: sso_session_id.to_string(),
            operator: view.operator,
            customer: view.customer,
        }))
    }
}

impl SsoApi for SsoClient {
    fn fetch<'a>(&'a self, sso_session_id: &'a str) -> BoxFuture<'a, Result<Option<SsoIdentity>, SsoUnavailable>> {
        Box::pin(self.fetch_identity(sso_session_id))
    }
}
