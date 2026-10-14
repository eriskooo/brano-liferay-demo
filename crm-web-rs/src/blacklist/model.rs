//! Doména blacklistu ne-zákazníka – rovnaké pojmy a hodnoty ako Java verzia (vymyslené, zjednodušené).

use std::sync::LazyLock;

use chrono::{NaiveDate, NaiveDateTime};
use regex::Regex;

named_enum!(BlacklistState { Active => "ACTIVE", Inactive => "INACTIVE" });

named_enum!(
    /// Dôvod zaradenia na blacklist.
    StateReason { Fraud => "FRAUD", Debt => "DEBT", Abuse => "ABUSE" }
);

named_enum!(
    /// Podmienka, ktorá sa uplatní pri objednávke osoby z blacklistu.
    ConditionType { BlockAll => "BLOCK_ALL", RequireDeposit => "REQUIRE_DEPOSIT", ManualApproval => "MANUAL_APPROVAL" }
);

named_enum!(
    /// Typ identifikátora osoby.
    IdentifierType {
        BirthCode => "BIRTH_CODE",
        IdCard => "ID_CARD",
        Passport => "PASSPORT",
        CompanyRegNo => "COMPANY_REG_NO",
    }
);

impl IdentifierType {
    /// Formát hodnoty ako Java regex (`matches()` = celý reťazec); `[0-9]`, lebo Rust `\d` berie aj Unicode číslice.
    pub fn is_valid(self, value: &str) -> bool {
        static FORMATS: LazyLock<[Regex; 4]> = LazyLock::new(|| {
            [r"^[0-9]{6}/?[0-9]{3,4}$", r"^[A-Z]{2}[0-9]{6}$", r"^[A-Z][0-9]{7}$", r"^[0-9]{8}$"]
                .map(|pattern| Regex::new(pattern).expect("platný regex"))
        });
        let index = match self {
            IdentifierType::BirthCode => 0,
            IdentifierType::IdCard => 1,
            IdentifierType::Passport => 2,
            IdentifierType::CompanyRegNo => 3,
        };
        FORMATS[index].is_match(value)
    }
}

/// Identifikátor osoby (`kind`, lebo `type` je v Ruste kľúčové slovo).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Identifier {
    pub id: u64,
    pub kind: IdentifierType,
    pub value: String,
}

/// Poznámka k záznamu.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Note {
    pub text: String,
    pub author: String,
    pub created_at: NaiveDateTime,
}

impl Note {
    /// Čas ako Java `LocalDateTime.toString()` pri presnosti na minúty (`2024-01-10T09:00`).
    pub fn created_at_text(&self) -> String {
        self.created_at.format("%Y-%m-%dT%H:%M").to_string()
    }
}

/// Záznam blacklistu (osoba, ktorá nie je zákazníkom, ale má obmedzené objednávky).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BlacklistRecord {
    pub id: u64,
    pub first_name: String,
    pub last_name: String,
    pub birth_date: NaiveDate,
    pub state: BlacklistState,
    pub state_reason: StateReason,
    pub condition_type: ConditionType,
    pub valid_from: NaiveDate,
    pub valid_to: Option<NaiveDate>,
    pub identifiers: Vec<Identifier>,
    pub notes: Vec<Note>,
}

impl BlacklistRecord {
    /// Meno a priezvisko.
    pub fn full_name(&self) -> String {
        format!("{} {}", self.first_name, self.last_name)
    }

    /// „Platné do“ ako ISO dátum, prázdne ak je neobmedzené.
    pub fn valid_to_text(&self) -> String {
        self.valid_to.map(|d| d.to_string()).unwrap_or_default()
    }
}

/// Príkaz na vytvorenie záznamu – backend port nezávisí od webového formulára.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct NewBlacklistRecord {
    pub first_name: String,
    pub last_name: String,
    pub birth_date: NaiveDate,
    pub state_reason: StateReason,
    pub condition_type: ConditionType,
    pub valid_from: NaiveDate,
    pub valid_to: Option<NaiveDate>,
    pub identifier_type: IdentifierType,
    pub identifier_value: String,
}

/// Dotaz na backend (Java `BlacklistSearchCriteria` v úlohe dotazu).
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct SearchCriteria {
    pub first_name: Option<String>,
    pub last_name: Option<String>,
    pub identifier_value: Option<String>,
    pub state: Option<BlacklistState>,
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Formáty identifikátorov ako Java regexy; číslice len ASCII (Java `\d`, nie Unicode).
    #[test]
    fn should_validate_identifier_formats_when_checked() {
        assert!(IdentifierType::BirthCode.is_valid("800512/1234"));
        assert!(IdentifierType::BirthCode.is_valid("8005121234"));
        assert!(!IdentifierType::BirthCode.is_valid("800512/12"));
        assert!(IdentifierType::IdCard.is_valid("AB123456"));
        assert!(!IdentifierType::IdCard.is_valid("ab123456"));
        assert!(IdentifierType::Passport.is_valid("P1234567"));
        assert!(IdentifierType::CompanyRegNo.is_valid("12345678"));
        assert!(!IdentifierType::CompanyRegNo.is_valid("１２３４５６７８"));
        assert!(!IdentifierType::CompanyRegNo.is_valid(" 12345678"));
    }

    /// Mená enumov sú Java mená (formuláre, i18n kľúče).
    #[test]
    fn should_round_trip_java_names_when_parsing() {
        assert_eq!(StateReason::from_name("DEBT"), Some(StateReason::Debt));
        assert_eq!(ConditionType::ManualApproval.name(), "MANUAL_APPROVAL");
        assert_eq!(BlacklistState::from_name(""), None);
        assert_eq!(IdentifierType::ALL.len(), 4);
    }
}
