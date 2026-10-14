//! Register zdieľaných kľúčov a ich JSON tvar – rovnaké kľúče ako `SharedSessionKeys` v Jave a `SharedKeys`
//! v starej appke. Zdieľa sa len to, čo implementuje `SharedValue`; typ nahrádza Java mapu kľúč → typ.

use serde::de::DeserializeOwned;
use serde::{Deserialize, Serialize};

pub const MESSAGES: &str = "APP_MESSAGES";
pub const LAST_SEARCH_CRITERIA: &str = "APP_BLACKLIST_LAST_CRITERIA";
pub const LAST_VIEWED_RECORD: &str = "APP_LAST_VIEWED_RECORD";

/// Hodnota zdieľanej session s pevným kľúčom v hashi.
pub trait SharedValue: Serialize + DeserializeOwned {
    /// Pole v hashi `crm:session:{SSO_ID}` (s prefixom APP_).
    const KEY: &'static str;
}

/// Naposledy otvorený záznam – hlavička všetkých appiek ho ukazuje.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LastViewedRecord {
    pub id: u64,
    pub full_name: String,
}

impl SharedValue for LastViewedRecord {
    const KEY: &'static str = LAST_VIEWED_RECORD;
}

/// Posledné kritériá vyhľadávania – len reťazce, `null` ako v Jave (neodoslané pole).
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LastSearchCriteria {
    pub first_name: Option<String>,
    pub last_name: Option<String>,
    pub identifier_value: Option<String>,
    pub state: Option<String>,
}

impl SharedValue for LastSearchCriteria {
    const KEY: &'static str = LAST_SEARCH_CRITERIA;
}

/// Úroveň hlášky – v JSON ako "INFO"/"ERROR".
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "UPPERCASE")]
pub enum Level {
    Info,
    Error,
}

/// Hláška (i18n kľúč) – tvar {"level","key"} je spoločný pre všetky appky.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct UiMessage {
    pub level: Level,
    pub key: String,
}

impl UiMessage {
    /// Informačná hláška.
    pub fn info(key: &str) -> Self {
        Self { level: Level::Info, key: key.to_string() }
    }

    /// Chybová hláška.
    pub fn error(key: &str) -> Self {
        Self { level: Level::Error, key: key.to_string() }
    }

    /// CSS trieda Liferay theme, ktorou hlavička hlášku vykreslí.
    pub fn css_class(&self) -> &'static str {
        match self.level {
            Level::Info => "portlet-msg-success",
            Level::Error => "portlet-msg-error",
        }
    }
}

/// Zoznam hlášok v `APP_MESSAGES` (JSON pole).
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(transparent)]
pub struct UiMessages(pub Vec<UiMessage>);

impl SharedValue for UiMessages {
    const KEY: &'static str = MESSAGES;
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Zápis má rovnaký JSON ako Java records (poradie polí, null, enum ako reťazec, diakritika bez escapovania).
    #[test]
    fn should_serialize_like_java_when_shared_values_written() {
        let criteria = LastSearchCriteria {
            first_name: Some("Mária".into()),
            last_name: Some(String::new()),
            identifier_value: None,
            state: Some("ACTIVE".into()),
        };
        assert_eq!(
            serde_json::to_string(&criteria).expect("json"),
            r#"{"firstName":"Mária","lastName":"","identifierValue":null,"state":"ACTIVE"}"#
        );
        let record = LastViewedRecord { id: 1, full_name: "Peter Vzorka".into() };
        assert_eq!(serde_json::to_string(&record).expect("json"), r#"{"id":1,"fullName":"Peter Vzorka"}"#);
        let messages = UiMessages(vec![UiMessage::info("a"), UiMessage::error("b")]);
        assert_eq!(
            serde_json::to_string(&messages).expect("json"),
            r#"[{"level":"INFO","key":"a"},{"level":"ERROR","key":"b"}]"#
        );
    }

    /// JSON zo starej/Java appky s null, chýbajúcim a neznámym poľom sa prečíta.
    #[test]
    fn should_read_legacy_json_when_nulls_missing_and_unknown_fields() {
        let record: LastViewedRecord = serde_json::from_str(r#"{"id":7,"fullName":"X","extra":true}"#).expect("json");
        assert_eq!(record, LastViewedRecord { id: 7, full_name: "X".into() });
        let criteria: LastSearchCriteria =
            serde_json::from_str(r#"{"firstName":null,"lastName":"Vzork","state":null,"newField":1}"#).expect("json");
        assert_eq!(criteria.last_name.as_deref(), Some("Vzork"));
        assert_eq!(criteria.identifier_value, None);
    }

    /// Hláška chyby a info majú CSS triedy Liferay theme.
    #[test]
    fn should_map_level_to_theme_class_when_rendering() {
        assert_eq!(UiMessage::info("k").css_class(), "portlet-msg-success");
        assert_eq!(UiMessage::error("k").css_class(), "portlet-msg-error");
    }
}
