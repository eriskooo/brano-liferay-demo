//! Pomocné rozhodnutia o requeste (Java `WebRequests` + čítanie cookies servletom).

use axum::http::HeaderMap;
use axum::http::header::COOKIE;

/// Hodnota cookie podľa mena zo všetkých hlavičiek `Cookie`.
pub fn cookie(headers: &HeaderMap, name: &str) -> Option<String> {
    headers
        .get_all(COOKIE)
        .iter()
        .filter_map(|value| value.to_str().ok())
        .flat_map(|value| value.split(';'))
        .filter_map(|pair| pair.trim().split_once('='))
        .find(|(key, _)| *key == name)
        .map(|(_, value)| value.to_string())
}

/// AJAX volanie (náhrada portletovej resource fázy) – chyby sa vracajú bez layoutu a bez redirectu.
pub fn is_ajax(headers: &HeaderMap) -> bool {
    headers.get("x-requested-with").is_some_and(|value| value == "XMLHttpRequest")
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::http::HeaderValue;
    use axum::http::header::COOKIE;

    /// Cookie sa nájde medzi viacerými (aj vo viacerých hlavičkách), podobné meno sa nezamení.
    #[test]
    fn should_find_cookie_among_many_when_parsing() {
        let mut headers = HeaderMap::new();
        headers.append(COOKIE, HeaderValue::from_static("a=1; SSO_IDX=zly ;SSO_ID=abc-1"));
        headers.append(COOKIE, HeaderValue::from_static("CRM_RS_SESSION=s-1"));
        assert_eq!(cookie(&headers, "SSO_ID").as_deref(), Some("abc-1"));
        assert_eq!(cookie(&headers, "CRM_RS_SESSION").as_deref(), Some("s-1"));
        assert_eq!(cookie(&headers, "chyba"), None);
        assert_eq!(cookie(&HeaderMap::new(), "SSO_ID"), None);
    }

    /// AJAX = hlavička X-Requested-With: XMLHttpRequest (crm.js ju posiela).
    #[test]
    fn should_detect_ajax_when_header_present() {
        let mut headers = HeaderMap::new();
        assert!(!is_ajax(&headers));
        headers.insert("x-requested-with", HeaderValue::from_static("XMLHttpRequest"));
        assert!(is_ajax(&headers));
    }
}
