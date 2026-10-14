//! Konfigurácia z premenných prostredia – defaulty zodpovedajú `application.properties` Java verzie (lokálny beh),
//! docker-compose prepíše SSO a Redis.

use std::net::SocketAddr;

/// Nastavenie appky (Java: `application.properties` + premenné prostredia v compose).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Config {
    pub bind: SocketAddr,
    pub sso_api_url: String,
    pub sso_login_url: String,
    pub redis_url: String,
    /// Statické CSS/JS sa servírujú priamo z crm-web (jeden zdroj s Java appkou); v Dockeri `/app/static`.
    pub static_dir: String,
}

impl Config {
    /// Konfigurácia z prostredia procesu.
    pub fn from_env() -> Self {
        Self::from_lookup(|name| std::env::var(name).ok())
    }

    /// Testovateľné jadro `from_env` – hodnoty premenných dodá volajúci.
    pub fn from_lookup(lookup: impl Fn(&str) -> Option<String>) -> Self {
        let get = |name: &str, default: &str| {
            lookup(name).filter(|v| !v.trim().is_empty()).unwrap_or_else(|| default.to_string())
        };
        let bind_text = get("CRM_BIND", "0.0.0.0:8080");
        let bind = bind_text.parse().unwrap_or_else(|_| {
            tracing::warn!("Neplatné CRM_BIND '{bind_text}', použije sa 0.0.0.0:8080");
            SocketAddr::from(([0, 0, 0, 0], 8080))
        });
        Self {
            bind,
            sso_api_url: get("CRM_SSO_APIURL", "http://localhost:8090/sso").trim_end_matches('/').to_string(),
            sso_login_url: get("CRM_SSO_LOGINURL", "/sso/login"),
            redis_url: format!("redis://{}:{}/", get("CRM_REDIS_HOST", "localhost"), get("CRM_REDIS_PORT", "6379")),
            static_dir: get("CRM_STATIC_DIR", "../crm-web/src/main/resources/static"),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    fn lookup(pairs: &[(&str, &str)]) -> impl Fn(&str) -> Option<String> {
        let map: HashMap<String, String> = pairs.iter().map(|(k, v)| (k.to_string(), v.to_string())).collect();
        move |name| map.get(name).cloned()
    }

    /// Bez premenných prostredia platia defaulty ako application.properties Java verzie.
    #[test]
    fn should_use_local_defaults_when_env_empty() {
        let config = Config::from_lookup(lookup(&[]));
        assert_eq!(config.bind.to_string(), "0.0.0.0:8080");
        assert_eq!(config.sso_api_url, "http://localhost:8090/sso");
        assert_eq!(config.sso_login_url, "/sso/login");
        assert_eq!(config.redis_url, "redis://localhost:6379/");
        assert_eq!(config.static_dir, "../crm-web/src/main/resources/static");
    }

    /// Docker compose prepíše SSO a Redis; koncové lomítko URL sa odstráni.
    #[test]
    fn should_override_defaults_when_env_set() {
        let config = Config::from_lookup(lookup(&[
            ("CRM_SSO_APIURL", "http://sso-mock:8090/sso/"),
            ("CRM_REDIS_HOST", "redis"),
            ("CRM_REDIS_PORT", "6380"),
            ("CRM_STATIC_DIR", "/app/static"),
            ("CRM_BIND", "127.0.0.1:9000"),
        ]));
        assert_eq!(config.sso_api_url, "http://sso-mock:8090/sso");
        assert_eq!(config.redis_url, "redis://redis:6380/");
        assert_eq!(config.static_dir, "/app/static");
        assert_eq!(config.bind.to_string(), "127.0.0.1:9000");
    }

    /// Prázdna hodnota alebo neplatná adresa = default, nie pád pri štarte.
    #[test]
    fn should_fall_back_to_default_when_value_blank_or_invalid() {
        let config = Config::from_lookup(lookup(&[("CRM_REDIS_HOST", "  "), ("CRM_BIND", "nie-adresa")]));
        assert_eq!(config.redis_url, "redis://localhost:6379/");
        assert_eq!(config.bind.to_string(), "0.0.0.0:8080");
    }
}
