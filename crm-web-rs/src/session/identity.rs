//! Kto je prihlásený – identitu vlastní SSO portál, appka ju len číta (Java `SsoIdentity`, `IdentityResolver`).

use std::fmt;

use serde::Deserialize;

use crate::BoxFuture;

/// Cookie SSO portálu – tú istú čítajú Liferay aj Java appka.
pub const SSO_COOKIE: &str = "SSO_ID";

/// Operátor prihlásený v SSO portáli (legacy: PortalOperator zo sso-portal-api).
#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct OperatorUser {
    pub login: String,
    pub display_name: String,
}

/// Zákazník vybraný v SSO portáli – CRM ho nemení, len číta.
#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CustomerContext {
    pub customer_id: String,
    pub display_name: String,
}

/// Identita portálovej session: jej ID (kľúč zdieľanej session v Redise), operátor a zákazník.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SsoIdentity {
    pub sso_session_id: String,
    pub operator: OperatorUser,
    pub customer: CustomerContext,
}

/// SSO portál neodpovedá alebo odpovedá chybou – nie je to „neprihlásený“ (Java `SsoUnavailableException`).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SsoUnavailable(pub String);

impl fmt::Display for SsoUnavailable {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "SSO portál je nedostupný: {}", self.0)
    }
}

/// Port na SSO portál; v testoch náhrada bez siete.
pub trait SsoApi: Send + Sync {
    /// Identita session, `Ok(None)` ak ju portál nepozná.
    fn fetch<'a>(&'a self, sso_session_id: &'a str) -> BoxFuture<'a, Result<Option<SsoIdentity>, SsoUnavailable>>;
}

/// Platný tvar SSO_ID – hodnota ide do URL portálu aj do kľúča v Redise, iný tvar = neprihlásený.
pub fn is_valid_sso_id(value: &str) -> bool {
    (1..=64).contains(&value.len()) && value.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-')
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Platný tvar SSO_ID; poškodená hodnota (lomka, medzera, prázdna, dlhá) = neprihlásený.
    #[test]
    fn should_validate_sso_id_shape_when_checked() {
        assert!(is_valid_sso_id("3f2a-9C"));
        assert!(is_valid_sso_id(&"a".repeat(64)));
        assert!(!is_valid_sso_id(""));
        assert!(!is_valid_sso_id(&"a".repeat(65)));
        assert!(!is_valid_sso_id("../x"));
        assert!(!is_valid_sso_id("a b"));
    }
}
