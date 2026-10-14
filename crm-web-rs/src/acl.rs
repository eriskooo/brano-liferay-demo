//! ACL – v legacy vlastná ACL služba backendu; demo zachováva model „privilégium na operátora“ (security sa nemení).

use std::collections::{HashMap, HashSet};

use crate::session::identity::OperatorUser;

named_enum!(
    /// Privilégiá – mená sa v šablónach používajú ako reťazec, rovnako ako atribút `privilegeType` v JSP.
    Privilege {
        BlacklistView => "BLACKLIST_VIEW",
        BlacklistModify => "BLACKLIST_MODIFY",
        Deactivation => "DEACTIVATION",
    }
);

/// Port na ACL (Java `AclService`).
pub trait AclService: Send + Sync {
    /// Všetky privilégiá operátora.
    fn privileges_of(&self, operator: &OperatorUser) -> HashSet<Privilege>;

    /// Má operátor privilégium?
    fn has_privilege(&self, operator: &OperatorUser, privilege: Privilege) -> bool {
        self.privileges_of(operator).contains(&privilege)
    }
}

/// Pevné privilégiá demo operátorov (loginy zodpovedajú SSO mocku).
pub struct InMemoryAclService {
    by_login: HashMap<&'static str, HashSet<Privilege>>,
}

impl Default for InMemoryAclService {
    fn default() -> Self {
        let by_login = HashMap::from([
            ("citatel", HashSet::from([Privilege::BlacklistView])),
            ("spravca", Privilege::ALL.iter().copied().collect()),
        ]);
        Self { by_login }
    }
}

impl AclService for InMemoryAclService {
    fn privileges_of(&self, operator: &OperatorUser) -> HashSet<Privilege> {
        self.by_login.get(operator.login.as_str()).cloned().unwrap_or_default()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn operator(login: &str) -> OperatorUser {
        OperatorUser { login: login.to_string(), display_name: login.to_string() }
    }

    /// Správca má všetko, čitateľ len prezeranie, neznámy login nič (rovnako ako Java InMemoryAclService).
    #[test]
    fn should_grant_privileges_by_login_when_asked() {
        let acl = InMemoryAclService::default();
        assert_eq!(acl.privileges_of(&operator("spravca")), Privilege::ALL.iter().copied().collect());
        assert!(acl.has_privilege(&operator("citatel"), Privilege::BlacklistView));
        assert!(!acl.has_privilege(&operator("citatel"), Privilege::BlacklistModify));
        assert!(acl.privileges_of(&operator("nikto")).is_empty());
    }

    /// Mená privilégií sú rovnaké ako v JSP atribúte privilegeType.
    #[test]
    fn should_map_jsp_names_when_parsing_privilege() {
        assert_eq!(Privilege::from_name("BLACKLIST_MODIFY"), Some(Privilege::BlacklistModify));
        assert_eq!(Privilege::BlacklistView.name(), "BLACKLIST_VIEW");
        assert_eq!(Privilege::from_name("blacklist_view"), None);
    }
}
