//! Port na backend blacklistu + in-memory náhrada (vymyslené dáta, seed zhodný s Java appkou). Reálna migrácia
//! sem pripojí adaptér na existujúci backend – handlery sa nemenia.

use std::collections::BTreeMap;
use std::sync::{Arc, Mutex, MutexGuard, PoisonError};

use chrono::{NaiveDate, NaiveDateTime, Timelike};

use crate::blacklist::model::{
    BlacklistRecord, BlacklistState, ConditionType, Identifier, IdentifierType, NewBlacklistRecord, Note,
    SearchCriteria, StateReason,
};
use crate::clock::Clock;

/// Chyba backendu – web vrstva z `NotFound` spraví 404, `Rule` nesie i18n kľúč hlášky.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum BlacklistError {
    NotFound(String),
    Rule(&'static str),
}

/// Port na backend blacklistu (Java `BlacklistService`).
pub trait BlacklistService: Send + Sync {
    /// Záznamy podľa kritérií v poradí ID.
    fn search(&self, criteria: &SearchCriteria) -> Vec<BlacklistRecord>;
    /// Záznam podľa ID.
    fn find_by_id(&self, id: u64) -> Option<BlacklistRecord>;
    /// Vytvorí aktívny záznam s jedným identifikátorom.
    fn create(&self, data: NewBlacklistRecord) -> BlacklistRecord;
    /// Zmena stavu a platnosti; deaktivácia bez „platné do“ doplní dnešný dátum.
    fn change(&self, id: u64, state: BlacklistState, valid_to: Option<NaiveDate>) -> Result<(), BlacklistError>;
    /// Pridá poznámku (text sa oreže, čas na minúty).
    fn add_note(&self, id: u64, text: &str, author: &str) -> Result<(), BlacklistError>;
    /// Vráti zostávajúce identifikátory; posledný sa odstrániť nedá.
    fn remove_identifier(&self, record_id: u64, identifier_id: u64) -> Result<Vec<Identifier>, BlacklistError>;
    /// Povolené podmienky pre dôvod; `None` = všetky.
    fn allowed_condition_types(&self, reason: Option<StateReason>) -> Vec<ConditionType>;
}

struct Inner {
    records: BTreeMap<u64, BlacklistRecord>,
    next_record_id: u64,
    next_identifier_id: u64,
}

/// In-memory backend. Mutex – jednoduchosť pred výkonom (Java `synchronized`); reálny backend má transakcie.
pub struct InMemoryBlacklistService {
    clock: Arc<dyn Clock>,
    inner: Mutex<Inner>,
}

impl InMemoryBlacklistService {
    /// Služba so seed dátami.
    pub fn new(clock: Arc<dyn Clock>) -> Self {
        let mut inner = Inner { records: BTreeMap::new(), next_record_id: 1, next_identifier_id: 1 };
        seed(&mut inner);
        Self { clock, inner: Mutex::new(inner) }
    }

    fn lock(&self) -> MutexGuard<'_, Inner> {
        self.inner.lock().unwrap_or_else(PoisonError::into_inner)
    }
}

impl Inner {
    fn add(&mut self, name: (&str, &str), birth_date: NaiveDate, state: BlacklistState,
           rule: (StateReason, ConditionType), validity: (NaiveDate, Option<NaiveDate>)) -> u64 {
        let id = self.next_record_id;
        self.next_record_id += 1;
        self.records.insert(id, BlacklistRecord {
            id,
            first_name: name.0.to_string(),
            last_name: name.1.to_string(),
            birth_date,
            state,
            state_reason: rule.0,
            condition_type: rule.1,
            valid_from: validity.0,
            valid_to: validity.1,
            identifiers: Vec::new(),
            notes: Vec::new(),
        });
        id
    }

    fn identifier(&mut self, record_id: u64, kind: IdentifierType, value: &str) {
        let id = self.next_identifier_id;
        self.next_identifier_id += 1;
        if let Some(record) = self.records.get_mut(&record_id) {
            record.identifiers.push(Identifier { id, kind, value: value.to_string() });
        }
    }

    fn require(&mut self, id: u64) -> Result<&mut BlacklistRecord, BlacklistError> {
        self.records.get_mut(&id).ok_or_else(|| BlacklistError::NotFound(format!("záznam blacklistu {id}")))
    }
}

impl BlacklistService for InMemoryBlacklistService {
    fn search(&self, criteria: &SearchCriteria) -> Vec<BlacklistRecord> {
        self.lock().records.values().filter(|r| matches(r, criteria)).cloned().collect()
    }

    fn find_by_id(&self, id: u64) -> Option<BlacklistRecord> {
        self.lock().records.get(&id).cloned()
    }

    fn create(&self, data: NewBlacklistRecord) -> BlacklistRecord {
        let mut inner = self.lock();
        let id = inner.add((&data.first_name, &data.last_name), data.birth_date, BlacklistState::Active,
            (data.state_reason, data.condition_type), (data.valid_from, data.valid_to));
        inner.identifier(id, data.identifier_type, &data.identifier_value);
        inner.records[&id].clone()
    }

    fn change(&self, id: u64, state: BlacklistState, valid_to: Option<NaiveDate>) -> Result<(), BlacklistError> {
        let effective = match (state, valid_to) {
            (BlacklistState::Inactive, None) => Some(self.clock.today()),
            _ => valid_to,
        };
        let mut inner = self.lock();
        let record = inner.require(id)?;
        record.state = state;
        record.valid_to = effective;
        Ok(())
    }

    fn add_note(&self, id: u64, text: &str, author: &str) -> Result<(), BlacklistError> {
        let now = self.clock.now();
        let created_at = now.with_second(0).and_then(|t| t.with_nanosecond(0)).unwrap_or(now);
        let note = Note { text: text.trim().to_string(), author: author.to_string(), created_at };
        self.lock().require(id)?.notes.push(note);
        Ok(())
    }

    fn remove_identifier(&self, record_id: u64, identifier_id: u64) -> Result<Vec<Identifier>, BlacklistError> {
        let mut inner = self.lock();
        let record = inner.require(record_id)?;
        // Identifikátor iného záznamu = podvrhnuté ID → 404, nie zmena cudzích dát.
        let Some(position) = record.identifiers.iter().position(|i| i.id == identifier_id) else {
            return Err(BlacklistError::NotFound(format!("identifikátor {identifier_id} záznamu {record_id}")));
        };
        if record.identifiers.len() == 1 {
            return Err(BlacklistError::Rule("blacklist.detail.lastIdentifier"));
        }
        record.identifiers.remove(position);
        Ok(record.identifiers.clone())
    }

    fn allowed_condition_types(&self, reason: Option<StateReason>) -> Vec<ConditionType> {
        match reason {
            None => ConditionType::ALL.to_vec(),
            Some(StateReason::Fraud) => vec![ConditionType::BlockAll],
            Some(StateReason::Debt) => vec![ConditionType::RequireDeposit, ConditionType::ManualApproval],
            Some(StateReason::Abuse) => vec![ConditionType::ManualApproval, ConditionType::BlockAll],
        }
    }
}

fn matches(record: &BlacklistRecord, criteria: &SearchCriteria) -> bool {
    let identifier = non_blank(criteria.identifier_value.as_deref());
    contains_ignore_case(&record.first_name, criteria.first_name.as_deref())
        && contains_ignore_case(&record.last_name, criteria.last_name.as_deref())
        && identifier.is_none_or(|v| record.identifiers.iter().any(|i| i.value.to_lowercase() == v.to_lowercase()))
        && criteria.state.is_none_or(|state| state == record.state)
}

fn non_blank(value: Option<&str>) -> Option<&str> {
    value.map(str::trim).filter(|v| !v.is_empty())
}

fn contains_ignore_case(value: &str, query: Option<&str>) -> bool {
    non_blank(query).is_none_or(|q| value.to_lowercase().contains(&q.to_lowercase()))
}

fn date(year: i32, month: u32, day: u32) -> NaiveDate {
    NaiveDate::from_ymd_opt(year, month, day).expect("platný dátum v seed dátach")
}

fn at(day: NaiveDate, hour: u32) -> NaiveDateTime {
    day.and_hms_opt(hour, 0, 0).expect("platný čas v seed dátach")
}

/// Rovnaké dáta ako `InMemoryBlacklistService.seed()` Java verzie (ID záznamov 1–8, identifikátorov 1–10).
fn seed(inner: &mut Inner) {
    use BlacklistState::{Active, Inactive};
    use ConditionType::{BlockAll, ManualApproval, RequireDeposit};
    use IdentifierType::{BirthCode, CompanyRegNo, IdCard, Passport};
    use StateReason::{Abuse, Debt, Fraud};

    let r1 = inner.add(("Peter", "Vzorka"), date(1980, 5, 12), Active, (Fraud, BlockAll), (date(2024, 1, 10), None));
    inner.identifier(r1, BirthCode, "800512/1234");
    inner.identifier(r1, IdCard, "AB123456");
    if let Some(record) = inner.records.get_mut(&r1) {
        record.notes.push(Note {
            text: "Opakované pokusy o objednávku na cudzie doklady.".into(),
            author: "Systém".into(),
            created_at: at(date(2024, 1, 10), 9),
        });
    }
    let r2 = inner.add(("Mária", "Príkladová"), date(1975, 11, 3), Active, (Debt, RequireDeposit),
        (date(2023, 6, 1), Some(date(2027, 6, 1))));
    inner.identifier(r2, BirthCode, "755103/2345");
    let r3 = inner.add(("Ján", "Testovací"), date(1990, 2, 20), Inactive, (Debt, ManualApproval),
        (date(2022, 3, 15), Some(date(2024, 3, 15))));
    inner.identifier(r3, IdCard, "CD987654");
    let r4 = inner.add(("Eva", "Ukážková"), date(1988, 7, 30), Active, (Abuse, ManualApproval), (date(2025, 2, 1), None));
    inner.identifier(r4, Passport, "P1234567");
    let r5 = inner.add(("Tomáš", "Fiktívny"), date(1969, 12, 24), Active, (Fraud, BlockAll), (date(2021, 9, 9), None));
    inner.identifier(r5, BirthCode, "691224/3456");
    inner.identifier(r5, Passport, "X7654321");
    let r6 = inner.add(("Lucia", "Demová"), date(1995, 4, 18), Inactive, (Abuse, BlockAll),
        (date(2020, 1, 1), Some(date(2021, 1, 1))));
    inner.identifier(r6, IdCard, "EF555444");
    let r7 = inner.add(("Karol", "Modelový"), date(1983, 8, 8), Active, (Debt, RequireDeposit), (date(2025, 10, 1), None));
    inner.identifier(r7, CompanyRegNo, "12345678");
    let r8 = inner.add(("Peter", "Vzorkový"), date(1979, 1, 1), Active, (Debt, ManualApproval), (date(2024, 5, 5), None));
    inner.identifier(r8, BirthCode, "790101/4567");
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::clock::FixedClock;
    use crate::blacklist::model::{ConditionType::*, IdentifierType, StateReason};

    fn today() -> NaiveDate {
        NaiveDate::from_ymd_opt(2026, 10, 14).expect("dátum")
    }

    fn service() -> InMemoryBlacklistService {
        let now = today().and_hms_opt(10, 15, 42).expect("čas");
        InMemoryBlacklistService::new(Arc::new(FixedClock(now)))
    }

    fn ids(records: &[BlacklistRecord]) -> Vec<u64> {
        records.iter().map(|r| r.id).collect()
    }

    fn criteria() -> SearchCriteria {
        SearchCriteria::default()
    }

    /// Prázdne kritériá vrátia celý seed (8 záznamov, rovnaký ako Java) v poradí ID.
    #[test]
    fn should_return_all_seed_records_when_criteria_empty() {
        assert_eq!(ids(&service().search(&criteria())), [1, 2, 3, 4, 5, 6, 7, 8]);
    }

    /// Meno a priezvisko: podreťazec bez ohľadu na veľkosť písmen, s diakritikou, okolité medzery sa ignorujú.
    #[test]
    fn should_match_names_case_insensitive_when_searching() {
        let service = service();
        let maria = SearchCriteria { first_name: Some("márIA".into()), ..criteria() };
        assert_eq!(ids(&service.search(&maria)), [2]);
        let vzork = SearchCriteria { last_name: Some(" vzork ".into()), ..criteria() };
        assert_eq!(ids(&service.search(&vzork)), [1, 8]);
    }

    /// Identifikátor sa porovnáva celý (bez veľkosti písmen), stav presne.
    #[test]
    fn should_match_identifier_exactly_and_state_when_searching() {
        let service = service();
        let exact = SearchCriteria { identifier_value: Some("ab123456".into()), ..criteria() };
        assert_eq!(ids(&service.search(&exact)), [1]);
        let partial = SearchCriteria { identifier_value: Some("AB1234".into()), ..criteria() };
        assert!(service.search(&partial).is_empty());
        let inactive = SearchCriteria { state: Some(BlacklistState::Inactive), ..criteria() };
        assert_eq!(ids(&service.search(&inactive)), [3, 6]);
    }

    /// Deaktivácia bez „platné do“ doplní dnešný dátum; iná zmena nechá zadaný dátum.
    #[test]
    fn should_fill_today_when_deactivated_without_valid_to() {
        let service = service();
        service.change(7, BlacklistState::Inactive, None).expect("zmena");
        assert_eq!(service.find_by_id(7).expect("záznam").valid_to, Some(today()));
        let date = NaiveDate::from_ymd_opt(2030, 1, 1).expect("dátum");
        service.change(4, BlacklistState::Active, Some(date)).expect("zmena");
        assert_eq!(service.find_by_id(4).expect("záznam").valid_to, Some(date));
        assert!(matches!(service.change(999, BlacklistState::Active, None), Err(BlacklistError::NotFound(_))));
    }

    /// Poznámka sa oreže a čas sa zaokrúhli na minúty.
    #[test]
    fn should_add_trimmed_note_with_minute_precision_when_added() {
        let service = service();
        service.add_note(2, "  text  ", "Správca").expect("poznámka");
        let note = service.find_by_id(2).expect("záznam").notes.last().cloned().expect("poznámka");
        assert_eq!((note.text.as_str(), note.author.as_str()), ("text", "Správca"));
        assert_eq!(note.created_at_text(), "2026-10-14T10:15");
    }

    /// Posledný identifikátor sa odstrániť nedá; identifikátor iného záznamu = 404, cudzie dáta sa nezmenia.
    #[test]
    fn should_guard_identifiers_when_removing() {
        let service = service();
        assert_eq!(service.remove_identifier(2, 3), Err(BlacklistError::Rule("blacklist.detail.lastIdentifier")));
        assert!(matches!(service.remove_identifier(1, 3), Err(BlacklistError::NotFound(_))));
        assert_eq!(service.find_by_id(2).expect("záznam").identifiers.len(), 1);
        let remaining = service.remove_identifier(1, 2).expect("odstránenie");
        assert_eq!(remaining.iter().map(|i| i.id).collect::<Vec<_>>(), [1]);
    }

    /// Povolené podmienky podľa dôvodu; bez dôvodu všetky.
    #[test]
    fn should_allow_condition_types_by_reason_when_asked() {
        let service = service();
        assert_eq!(service.allowed_condition_types(None), [BlockAll, RequireDeposit, ManualApproval]);
        assert_eq!(service.allowed_condition_types(Some(StateReason::Fraud)), [BlockAll]);
        assert_eq!(service.allowed_condition_types(Some(StateReason::Debt)), [RequireDeposit, ManualApproval]);
        assert_eq!(service.allowed_condition_types(Some(StateReason::Abuse)), [ManualApproval, BlockAll]);
    }

    /// Nový záznam je aktívny, dostane ďalšie ID a jeden identifikátor; vyhľadávanie ho nájde.
    #[test]
    fn should_create_active_record_when_created() {
        let service = service();
        let created = service.create(NewBlacklistRecord {
            first_name: "Nový".into(),
            last_name: "Človek".into(),
            birth_date: NaiveDate::from_ymd_opt(1990, 1, 1).expect("dátum"),
            state_reason: StateReason::Debt,
            condition_type: RequireDeposit,
            valid_from: today(),
            valid_to: None,
            identifier_type: IdentifierType::IdCard,
            identifier_value: "AB654321".into(),
        });
        assert_eq!((created.id, created.state), (9, BlacklistState::Active));
        assert_eq!(created.identifiers.iter().map(|i| i.id).collect::<Vec<_>>(), [11]);
        let found = SearchCriteria { last_name: Some("človek".into()), ..criteria() };
        assert_eq!(ids(&service.search(&found)), [9]);
    }
}
