//! Webové formuláre blacklistu a ich validácia – náhrada Bean Validation (`@NotBlank`, `@Past`, `@AssertTrue`).
//! Polia sú surové reťazce ako ich naviaže Spring; chyba sa viaže na rovnaký `path` ako v JSP `form:errors`.

use chrono::NaiveDate;
use serde::Deserialize;

use crate::blacklist::model::{
    BlacklistRecord, BlacklistState, ConditionType, IdentifierType, NewBlacklistRecord, SearchCriteria, StateReason,
};
use crate::session::keys::LastSearchCriteria;

pub const REQUIRED: &str = "validation.required";
/// Neplatný dátum/enum (Java by ukázala technickú hlášku typeMismatch).
pub const INVALID: &str = "validation.invalid";
const NOTE_MAX_LENGTH: usize = 500;

/// Porušené pravidlo: pole (`path` ako v JSP) a i18n kľúč hlášky.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Violation {
    pub path: &'static str,
    pub key: &'static str,
}

impl Violation {
    fn new(path: &'static str, key: &'static str) -> Self {
        Self { path, key }
    }
}

/// Hodnota sa nedá previesť na dátum/enum.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct InvalidValue;

fn non_blank(raw: &Option<String>) -> Option<&str> {
    raw.as_deref().map(str::trim).filter(|v| !v.is_empty())
}

fn text(raw: &Option<String>) -> &str {
    raw.as_deref().unwrap_or("")
}

/// Enum z Java mena; prázdne = nevyplnené (Spring prázdny reťazec naviaže ako null).
pub fn parse_enum<T>(raw: &Option<String>, from_name: fn(&str) -> Option<T>) -> Result<Option<T>, InvalidValue> {
    non_blank(raw).map(|v| from_name(v).ok_or(InvalidValue)).transpose()
}

/// ISO dátum (`<input type="date">`); prázdne = nevyplnené.
pub fn parse_date(raw: &Option<String>) -> Result<Option<NaiveDate>, InvalidValue> {
    non_blank(raw).map(|v| NaiveDate::parse_from_str(v, "%Y-%m-%d").map_err(|_| InvalidValue)).transpose()
}

fn required<T>(parsed: Result<Option<T>, InvalidValue>, path: &'static str, out: &mut Vec<Violation>) -> Option<T> {
    match parsed {
        Ok(Some(value)) => Some(value),
        Ok(None) => {
            out.push(Violation::new(path, REQUIRED));
            None
        }
        Err(InvalidValue) => {
            out.push(Violation::new(path, INVALID));
            None
        }
    }
}

fn optional<T>(parsed: Result<Option<T>, InvalidValue>, path: &'static str, out: &mut Vec<Violation>) -> Option<T> {
    parsed.unwrap_or_else(|InvalidValue| {
        out.push(Violation::new(path, INVALID));
        None
    })
}

fn required_text(raw: &Option<String>, path: &'static str, out: &mut Vec<Violation>) -> Option<String> {
    required(Ok(non_blank(raw).map(String::from)), path, out)
}

/// Kritériá vyhľadávania – zároveň formulár držaný v lokálnej session (Java `@SessionAttributes`).
#[derive(Debug, Clone, Default, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SearchForm {
    pub first_name: Option<String>,
    pub last_name: Option<String>,
    pub identifier_value: Option<String>,
    pub state: Option<String>,
}

impl SearchForm {
    /// Odoslané polia prepíšu kritériá zo session, neodoslané ostanú (binding na objekt zo session).
    pub fn merge(self, posted: SearchForm) -> SearchForm {
        SearchForm {
            first_name: posted.first_name.or(self.first_name),
            last_name: posted.last_name.or(self.last_name),
            identifier_value: posted.identifier_value.or(self.identifier_value),
            state: posted.state.or(self.state),
        }
    }

    /// Aspoň jedno kritérium (medzery sa nerátajú) a platný stav.
    pub fn validate(&self) -> Result<SearchCriteria, Vec<Violation>> {
        let state = parse_enum(&self.state, BlacklistState::from_name)
            .map_err(|_| vec![Violation::new("state", INVALID)])?;
        let any = [&self.first_name, &self.last_name, &self.identifier_value].into_iter().any(|v| non_blank(v).is_some());
        if !any && state.is_none() {
            return Err(vec![Violation::new("anyCriterionFilled", "blacklist.search.noCriteria")]);
        }
        Ok(self.criteria_with(state))
    }

    /// Dotaz pre výsledky; neplatný stav sa ignoruje (Java by ho vôbec nenaviazala).
    pub fn criteria(&self) -> SearchCriteria {
        self.criteria_with(parse_enum(&self.state, BlacklistState::from_name).ok().flatten())
    }

    fn criteria_with(&self, state: Option<BlacklistState>) -> SearchCriteria {
        SearchCriteria {
            first_name: self.first_name.clone(),
            last_name: self.last_name.clone(),
            identifier_value: self.identifier_value.clone(),
            state,
        }
    }

    /// Tvar pre zdieľanú session (Java: `LastSearchCriteria` z naviazaného formulára).
    pub fn to_shared(&self) -> LastSearchCriteria {
        let state = parse_enum(&self.state, BlacklistState::from_name).ok().flatten();
        LastSearchCriteria {
            first_name: self.first_name.clone(),
            last_name: self.last_name.clone(),
            identifier_value: self.identifier_value.clone(),
            state: state.map(|s| s.name().to_string()),
        }
    }

    /// Hodnota poľa pre šablónu (`path` ako v JSP).
    pub fn v(&self, path: &str) -> &str {
        match path {
            "firstName" => text(&self.first_name),
            "lastName" => text(&self.last_name),
            "identifierValue" => text(&self.identifier_value),
            "state" => text(&self.state),
            _ => "",
        }
    }
}

/// Formulár nového záznamu.
#[derive(Debug, Clone, Default, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateForm {
    pub first_name: Option<String>,
    pub last_name: Option<String>,
    pub birth_date: Option<String>,
    pub state_reason: Option<String>,
    pub condition_type: Option<String>,
    pub valid_from: Option<String>,
    pub valid_to: Option<String>,
    pub identifier_type: Option<String>,
    pub identifier_value: Option<String>,
}

impl CreateForm {
    /// Predvyplnený formulár (Java `@ModelAttribute` metóda: „platné od“ = dnes).
    pub fn initial(today: NaiveDate) -> Self {
        Self { valid_from: Some(today.to_string()), ..Self::default() }
    }

    /// Neodoslané „platné od“ ostane dnešné – Java naväzuje POST na predvyplnený formulár.
    pub fn with_default_valid_from(mut self, today: NaiveDate) -> Self {
        if self.valid_from.is_none() {
            self.valid_from = Some(today.to_string());
        }
        self
    }

    /// Zvolený dôvod (pre zoznam povolených podmienok); neplatný = žiadny.
    pub fn state_reason(&self) -> Option<StateReason> {
        parse_enum(&self.state_reason, StateReason::from_name).ok().flatten()
    }

    /// Pravidlá Java `BlacklistCreateForm` + kontrola povolenej podmienky z controllera.
    pub fn validate(&self, today: NaiveDate, allowed: impl Fn(Option<StateReason>) -> Vec<ConditionType>)
        -> Result<NewBlacklistRecord, Vec<Violation>> {
        let mut out = Vec::new();
        let first_name = required_text(&self.first_name, "firstName", &mut out);
        let last_name = required_text(&self.last_name, "lastName", &mut out);
        let birth_date = required(parse_date(&self.birth_date), "birthDate", &mut out);
        if birth_date.is_some_and(|d| d >= today) {
            out.push(Violation::new("birthDate", "validation.past"));
        }
        let state_reason = required(parse_enum(&self.state_reason, StateReason::from_name), "stateReason", &mut out);
        let condition_type = required(parse_enum(&self.condition_type, ConditionType::from_name), "conditionType", &mut out);
        let valid_from = required(parse_date(&self.valid_from), "validFrom", &mut out);
        let valid_to = optional(parse_date(&self.valid_to), "validTo", &mut out);
        let identifier_type = required(parse_enum(&self.identifier_type, IdentifierType::from_name), "identifierType", &mut out);
        let identifier_value = required_text(&self.identifier_value, "identifierValue", &mut out);
        cross_field_rules(&mut out, (valid_from, valid_to), (identifier_type, identifier_value.as_deref()),
            (state_reason, condition_type), allowed);
        match (first_name, last_name, birth_date, state_reason, condition_type, valid_from, identifier_type, identifier_value) {
            (Some(first_name), Some(last_name), Some(birth_date), Some(state_reason), Some(condition_type),
             Some(valid_from), Some(identifier_type), Some(identifier_value)) if out.is_empty() => Ok(NewBlacklistRecord {
                first_name, last_name, birth_date, state_reason, condition_type, valid_from, valid_to,
                identifier_type, identifier_value,
            }),
            _ => Err(out),
        }
    }

    /// Hodnota poľa pre šablónu (`path` ako v JSP).
    pub fn v(&self, path: &str) -> &str {
        match path {
            "firstName" => text(&self.first_name),
            "lastName" => text(&self.last_name),
            "birthDate" => text(&self.birth_date),
            "stateReason" => text(&self.state_reason),
            "conditionType" => text(&self.condition_type),
            "validFrom" => text(&self.valid_from),
            "validTo" => text(&self.valid_to),
            "identifierType" => text(&self.identifier_type),
            "identifierValue" => text(&self.identifier_value),
            _ => "",
        }
    }
}

/// Java `@AssertTrue isPeriodValid/isIdentifierValid` + pravidlo backendu o povolených podmienkach.
fn cross_field_rules(out: &mut Vec<Violation>, period: (Option<NaiveDate>, Option<NaiveDate>),
                     identifier: (Option<IdentifierType>, Option<&str>),
                     rule: (Option<StateReason>, Option<ConditionType>),
                     allowed: impl Fn(Option<StateReason>) -> Vec<ConditionType>) {
    if let (Some(from), Some(to)) = period {
        if to < from {
            out.push(Violation::new("periodValid", "blacklist.create.periodInvalid"));
        }
    }
    if let (Some(kind), Some(value)) = identifier {
        if !kind.is_valid(value) {
            out.push(Violation::new("identifierValid", "blacklist.create.identifierInvalid"));
        }
    }
    if let (Some(reason), Some(condition)) = rule {
        if !allowed(Some(reason)).contains(&condition) {
            out.push(Violation::new("conditionType", "blacklist.create.conditionNotAllowed"));
        }
    }
}

/// Formulár zmeny stavu/platnosti.
#[derive(Debug, Clone, Default, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChangeForm {
    pub state: Option<String>,
    pub valid_to: Option<String>,
}

impl ChangeForm {
    /// Predvyplnený aktuálnymi hodnotami záznamu.
    pub fn from_record(record: &BlacklistRecord) -> Self {
        Self { state: Some(record.state.name().to_string()), valid_to: record.valid_to.map(|d| d.to_string()) }
    }

    /// Stav povinný, „platné do“ voliteľné.
    pub fn validate(&self) -> Result<(BlacklistState, Option<NaiveDate>), Vec<Violation>> {
        let mut out = Vec::new();
        let state = required(parse_enum(&self.state, BlacklistState::from_name), "state", &mut out);
        let valid_to = optional(parse_date(&self.valid_to), "validTo", &mut out);
        match state {
            Some(state) if out.is_empty() => Ok((state, valid_to)),
            _ => Err(out),
        }
    }

    /// Hodnota poľa pre šablónu.
    pub fn v(&self, path: &str) -> &str {
        match path {
            "state" => text(&self.state),
            "validTo" => text(&self.valid_to),
            _ => "",
        }
    }
}

/// Formulár poznámky.
#[derive(Debug, Clone, Default, PartialEq, Eq, Deserialize)]
pub struct NoteForm {
    pub text: Option<String>,
}

impl NoteForm {
    /// `@NotBlank` + `@Size(max = 500)` (znaky, nie bajty).
    pub fn validate(&self) -> Result<String, Vec<Violation>> {
        match self.text.as_deref() {
            Some(t) if t.chars().count() > NOTE_MAX_LENGTH => Err(vec![Violation::new("text", "validation.maxLength")]),
            Some(t) if !t.trim().is_empty() => Ok(t.to_string()),
            _ => Err(vec![Violation::new("text", REQUIRED)]),
        }
    }

    /// Hodnota poľa pre šablónu.
    pub fn v(&self, path: &str) -> &str {
        match path {
            "text" => text(&self.text),
            _ => "",
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::blacklist::model::ConditionType::*;

    fn s(value: &str) -> Option<String> {
        Some(value.to_string())
    }

    fn today() -> NaiveDate {
        NaiveDate::from_ymd_opt(2026, 10, 14).expect("dátum")
    }

    /// Náhrada backendu: FRAUD dovolí len BLOCK_ALL, ostatné všetko.
    fn allowed(reason: Option<StateReason>) -> Vec<ConditionType> {
        match reason {
            Some(StateReason::Fraud) => vec![BlockAll],
            _ => ConditionType::ALL.to_vec(),
        }
    }

    fn valid_create() -> CreateForm {
        CreateForm {
            first_name: s("Nový"),
            last_name: s("Človek"),
            birth_date: s("1990-01-01"),
            state_reason: s("DEBT"),
            condition_type: s("REQUIRE_DEPOSIT"),
            valid_from: s("2026-10-14"),
            valid_to: s(""),
            identifier_type: s("ID_CARD"),
            identifier_value: s(" AB654321 "),
        }
    }

    fn paths(violations: &[Violation]) -> Vec<(&str, &str)> {
        violations.iter().map(|v| (v.path, v.key)).collect()
    }

    /// Vyhľadávanie bez kritéria (medzery sa nerátajú) je chyba celého formulára.
    #[test]
    fn should_require_any_criterion_when_all_blank() {
        let form = SearchForm { first_name: s("  "), last_name: s(""), identifier_value: None, state: s("") };
        assert_eq!(paths(&form.validate().unwrap_err()), [("anyCriterionFilled", "blacklist.search.noCriteria")]);
    }

    /// Samotný stav je kritérium; neznámy stav je neplatná hodnota.
    #[test]
    fn should_accept_state_only_and_reject_unknown_state_when_validating() {
        let only_state = SearchForm { state: s("INACTIVE"), ..SearchForm::default() };
        assert_eq!(only_state.validate().expect("platné").state, Some(BlacklistState::Inactive));
        let unknown = SearchForm { state: s("XYZ"), ..SearchForm::default() };
        assert_eq!(paths(&unknown.validate().unwrap_err()), [("state", INVALID)]);
    }

    /// @SessionAttributes: odoslané polia prepíšu kritériá zo session, neodoslané ostanú.
    #[test]
    fn should_keep_session_values_when_field_not_posted() {
        let previous = SearchForm { first_name: s("Peter"), last_name: s("X"), ..SearchForm::default() };
        let merged = previous.merge(SearchForm { last_name: s("Vzork"), ..SearchForm::default() });
        assert_eq!((merged.v("firstName"), merged.v("lastName"), merged.v("state")), ("Peter", "Vzork", ""));
    }

    /// Do zdieľanej session idú reťazce ako v Jave; prázdny stav je null.
    #[test]
    fn should_share_criteria_like_java_when_converted() {
        let form = SearchForm { first_name: s("Mária"), last_name: s(""), identifier_value: None, state: s("") };
        let shared = form.to_shared();
        assert_eq!(shared.first_name.as_deref(), Some("Mária"));
        assert_eq!(shared.last_name.as_deref(), Some(""));
        assert_eq!((shared.identifier_value, shared.state), (None, None));
    }

    /// Platný nový záznam: hodnoty orezané, prázdne „platné do“ = neobmedzene.
    #[test]
    fn should_build_command_when_create_form_valid() {
        let command = valid_create().validate(today(), allowed).expect("platné");
        assert_eq!(command.identifier_value, "AB654321");
        assert_eq!(command.valid_to, None);
        assert_eq!(command.condition_type, RequireDeposit);
    }

    /// Prázdny formulár: každé povinné pole má chybu validation.required.
    #[test]
    fn should_report_required_fields_when_create_form_empty() {
        let violations = CreateForm::default().validate(today(), allowed).unwrap_err();
        let expected = ["firstName", "lastName", "birthDate", "stateReason", "conditionType", "validFrom",
            "identifierType", "identifierValue"];
        assert_eq!(paths(&violations), expected.map(|p| (p, REQUIRED)));
    }

    /// Dátum narodenia musí byť v minulosti (dnes už nie), neplatný formát je neplatná hodnota.
    #[test]
    fn should_reject_birth_date_when_not_in_past_or_malformed() {
        let today_birth = CreateForm { birth_date: s("2026-10-14"), ..valid_create() };
        assert_eq!(paths(&today_birth.validate(today(), allowed).unwrap_err()), [("birthDate", "validation.past")]);
        let malformed = CreateForm { birth_date: s("14.10.1990"), ..valid_create() };
        assert_eq!(paths(&malformed.validate(today(), allowed).unwrap_err()), [("birthDate", INVALID)]);
        let yesterday = CreateForm { birth_date: s("2026-10-13"), ..valid_create() };
        assert!(yesterday.validate(today(), allowed).is_ok());
    }

    /// Krížové pravidlá: obdobie, formát identifikátora, povolená podmienka pre dôvod.
    #[test]
    fn should_check_cross_field_rules_when_validating_create() {
        let period = CreateForm { valid_to: s("2026-10-13"), ..valid_create() };
        assert_eq!(paths(&period.validate(today(), allowed).unwrap_err()), [("periodValid", "blacklist.create.periodInvalid")]);
        let same_day = CreateForm { valid_to: s("2026-10-14"), ..valid_create() };
        assert!(same_day.validate(today(), allowed).is_ok());
        let identifier = CreateForm { identifier_value: s("12"), ..valid_create() };
        assert_eq!(paths(&identifier.validate(today(), allowed).unwrap_err()),
            [("identifierValid", "blacklist.create.identifierInvalid")]);
        let condition = CreateForm { state_reason: s("FRAUD"), ..valid_create() };
        assert_eq!(paths(&condition.validate(today(), allowed).unwrap_err()),
            [("conditionType", "blacklist.create.conditionNotAllowed")]);
    }

    /// Neodoslané „platné od“ dostane dnešok (Java @ModelAttribute metóda pred bindingom).
    #[test]
    fn should_default_valid_from_when_not_posted() {
        let form = CreateForm { valid_from: None, ..valid_create() }.with_default_valid_from(today());
        assert_eq!(form.v("validFrom"), "2026-10-14");
        assert_eq!(CreateForm::initial(today()).v("validFrom"), "2026-10-14");
    }

    /// Zmena stavu: stav povinný, „platné do“ voliteľné, ale musí byť dátum.
    #[test]
    fn should_validate_change_form_when_submitted() {
        assert_eq!(ChangeForm { state: s("INACTIVE"), valid_to: s("") }.validate(), Ok((BlacklistState::Inactive, None)));
        assert_eq!(paths(&ChangeForm { state: s(""), valid_to: None }.validate().unwrap_err()), [("state", REQUIRED)]);
        assert_eq!(paths(&ChangeForm { state: s("ACTIVE"), valid_to: s("zajtra") }.validate().unwrap_err()),
            [("validTo", INVALID)]);
    }

    /// Poznámka: nesmie byť prázdna, max. 500 znakov (vrátane diakritiky).
    #[test]
    fn should_validate_note_length_when_submitted() {
        assert_eq!(paths(&NoteForm { text: s("   ") }.validate().unwrap_err()), [("text", REQUIRED)]);
        assert_eq!(paths(&NoteForm { text: Some("č".repeat(501)) }.validate().unwrap_err()),
            [("text", "validation.maxLength")]);
        assert!(NoteForm { text: Some("č".repeat(500)) }.validate().is_ok());
    }
}
