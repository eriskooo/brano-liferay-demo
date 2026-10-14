//! Texty UI. Jeden zdroj s Java appkou: `crm-web/.../messages.properties` sa vloží pri kompilácii,
//! `messages-rs.properties` prepíše len to, čím sa Rust appka líši.

use std::collections::HashMap;
use std::fmt::Display;

const BASE: &str = include_str!("../../../crm-web/src/main/resources/messages.properties");
const OVERRIDES: &str = include_str!("../../messages-rs.properties");

/// Preklady podľa kľúča (Java: Spring `MessageSource`).
#[derive(Debug, Clone)]
pub struct Messages {
    texts: HashMap<String, String>,
}

impl Messages {
    /// Texty crm-web + rozdiely Rust appky.
    pub fn load() -> Self {
        Self::from_sources(&[BASE, OVERRIDES])
    }

    /// Neskorší zdroj prepíše skorší (ako poradie basenames v Spring `MessageSource`).
    pub fn from_sources(sources: &[&str]) -> Self {
        Self { texts: sources.iter().flat_map(|source| parse(source)).collect() }
    }

    /// Text kľúča; chýbajúci kľúč je viditeľný `??kľúč??` + warn (Java `MessageTag`).
    pub fn get(&self, key: &str) -> String {
        match self.texts.get(key) {
            Some(text) => text.clone(),
            None => {
                tracing::warn!("Chýba i18n kľúč '{key}'");
                format!("??{key}??")
            }
        }
    }

    /// Text s argumentmi `{0}`, `{1}`… – podmnožina `MessageFormat`, ktorú texty používajú (bez apostrofov).
    pub fn format(&self, key: &str, args: &[&dyn Display]) -> String {
        args.iter()
            .enumerate()
            .fold(self.get(key), |text, (i, arg)| text.replace(&format!("{{{i}}}"), &arg.to_string()))
    }
}

/// Formát Java properties v rozsahu, ktorý súbor používa (bez pokračovacích riadkov a escape sekvencií).
fn parse(source: &str) -> Vec<(String, String)> {
    source
        .lines()
        .map(str::trim_start)
        .filter(|line| !line.is_empty() && !line.starts_with('#') && !line.starts_with('!'))
        .filter_map(|line| line.split_once('='))
        .map(|(key, value)| (key.trim().to_string(), value.trim_start().to_string()))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Neskorší zdroj prepíše skorší; komentáre a prázdne riadky sa ignorujú.
    #[test]
    fn should_override_earlier_source_when_key_repeated() {
        let messages = Messages::from_sources(&["# komentár\n\na=prvé\nb = druhé \n", "a=prepísané"]);
        assert_eq!(messages.get("a"), "prepísané");
        assert_eq!(messages.get("b"), "druhé ");
    }

    /// Chýbajúci kľúč je viditeľný (predchádzajúci PoC ho potichu zahodil).
    #[test]
    fn should_return_marked_key_when_missing() {
        assert_eq!(Messages::from_sources(&[]).get("x.y"), "??x.y??");
    }

    /// Argumenty {0}, {1} sa dosadia ako v MessageFormat.
    #[test]
    fn should_substitute_arguments_when_formatting() {
        let messages = Messages::from_sources(&["k=Počet: {0} z {1}"]);
        let args: [&dyn std::fmt::Display; 2] = [&3, &"10"];
        assert_eq!(messages.format("k", &args), "Počet: 3 z 10");
    }

    /// Hodnota môže obsahovať '=' (delí sa len prvým) a CRLF z checkoutu na Windows sa do textu nedostane.
    #[test]
    fn should_keep_equals_and_strip_crlf_when_parsing() {
        let messages = Messages::from_sources(&["k=a=b\r\nx=y\r\n"]);
        assert_eq!(messages.get("k"), "a=b");
        assert_eq!(messages.get("x"), "y");
    }

    /// Texty sú z crm-web, Rust appka mení len názov, ladiaci riadok a pridáva validation.invalid.
    #[test]
    fn should_load_java_texts_with_rust_overrides_when_loaded() {
        let messages = Messages::load();
        assert_eq!(messages.get("blacklist.search.title"), "Blacklist ne-zákazníkov – vyhľadávanie");
        assert_eq!(messages.get("app.name"), "CRM – nová appka (Rust)");
        assert_eq!(messages.get("header.newApp"), "Redis (Rust appka)");
        assert_eq!(messages.get("validation.invalid"), "Neplatná hodnota.");
    }
}
