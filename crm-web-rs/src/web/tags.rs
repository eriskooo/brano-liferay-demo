//! `Tags` = JSP taglib `portal:*` a `common:*` s rovnakým „rozhraním“ (mená, parametre), volaný zo šablón.
//! URL sa skladajú konvenciou `{ctx}{base}/{segment}?parametre` ako Java `PortletUrlTag`. Segment je
//! `&'static str` – v šablóne len literál, takže podvrhnutý segment sa nedá ani skompilovať (Java to kontroluje za behu).

use std::collections::HashSet;
use std::fmt::{self, Display};
use std::sync::Arc;

use crate::CONTEXT_PATH;
use crate::acl::Privilege;
use crate::blacklist::forms::Violation;
use crate::web::messages::Messages;

/// Base path „portletu“ blacklist (Java `@LegacyPortlet("/blacklist")`).
pub const PORTLET_BASE: &str = "/blacklist";

/// Plná cesta v rámci portletu, napr. `/search` → `/crm-rs/blacklist/search`.
pub fn portlet_path(path: &str) -> String {
    format!("{CONTEXT_PATH}{PORTLET_BASE}{path}")
}

/// URL kódovanie ako Java `URLEncoder.encode(…, UTF_8)`.
pub fn encode(value: &str) -> String {
    form_urlencoded::byte_serialize(value.as_bytes()).collect()
}

/// HTML escape textu (Spring `HtmlUtils.htmlEscape`).
pub fn escape_html(value: &str) -> String {
    value.replace('&', "&amp;").replace('<', "&lt;").replace('>', "&gt;").replace('"', "&quot;").replace('\'', "&#39;")
}

/// Kontext tagov pre jednu stránku: texty a privilégiá prihláseného operátora.
#[derive(Debug, Clone)]
pub struct Tags {
    messages: Arc<Messages>,
    privileges: Arc<HashSet<Privilege>>,
    /// Context path pre odkazy na statické súbory a navigáciu (`${pageContext.request.contextPath}`).
    pub ctx: &'static str,
}

impl Tags {
    /// Tagy pre operátora s danými privilégiami (prázdne = neprihlásený).
    pub fn new(messages: Arc<Messages>, privileges: HashSet<Privilege>) -> Self {
        Self { messages, privileges: Arc::new(privileges), ctx: CONTEXT_PATH }
    }

    /// Texty (pre `FieldErrors::resolve`).
    pub fn messages(&self) -> &Messages {
        &self.messages
    }

    /// `<common:message key="…"/>`.
    pub fn msg(&self, key: &str) -> String {
        self.messages.get(key)
    }

    /// `<common:message key="prefix.${enum}"/>`.
    pub fn msg_of(&self, prefix: &str, name: &str) -> String {
        self.messages.get(&format!("{prefix}{name}"))
    }

    /// `<common:message key="…"><common:msgarg value="…"/></common:message>`.
    pub fn msg_with(&self, key: &str, arg: impl Display) -> String {
        self.messages.format(key, &[&arg])
    }

    /// `<common:aclFilter privilegeType="…">` – telo len s privilégiom.
    pub fn can(&self, privilege_type: &str) -> bool {
        match Privilege::from_name(privilege_type) {
            Some(privilege) => self.has(privilege),
            None => {
                tracing::error!("Neznáme privilégium v šablóne: {privilege_type}");
                false
            }
        }
    }

    /// Má operátor privilégium?
    pub fn has(&self, privilege: Privilege) -> bool {
        self.privileges.contains(&privilege)
    }

    /// `<common:aclPageFilterTag>` – druhá línia obrany za `Require<P>`; `None` = stránka bez filtra.
    pub fn page_allowed(&self, privilege_type: &Option<&str>) -> bool {
        privilege_type.is_none_or(|p| self.can(p))
    }

    /// Text, ktorý page filter vypíše namiesto stránky.
    pub fn page_denied(&self, privilege_type: &Option<&str>) -> String {
        self.messages.format("acl.pageDenied", &[&privilege_type.unwrap_or("")])
    }

    /// `<portal:renderURL>` s `render`/`viewPage` = segment (GET).
    pub fn render(&self, segment: &'static str) -> PortletUrl {
        PortletUrl::new(portlet_path(&format!("/{segment}")))
    }

    /// `<portal:actionURL>` s `action` = segment (POST).
    pub fn action(&self, segment: &'static str) -> PortletUrl {
        PortletUrl::new(portlet_path(&format!("/{segment}")))
    }

    /// `<portal:resourceURL id="…">` (AJAX) pod `{base}/resource/{id}`.
    pub fn resource(&self, id: &'static str) -> PortletUrl {
        PortletUrl::new(portlet_path(&format!("/resource/{id}")))
    }
}

/// URL „portletu“ s parametrami (`<portal:param>`); v šablóne ju escapuje askama (escapeXml=true).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PortletUrl {
    path: String,
    query: Vec<(String, String)>,
}

impl PortletUrl {
    fn new(path: String) -> Self {
        Self { path, query: Vec::new() }
    }

    /// `<portal:param name value>`.
    pub fn param(mut self, name: &str, value: impl Display) -> Self {
        self.query.push((name.to_string(), value.to_string()));
        self
    }
}

impl Display for PortletUrl {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.path)?;
        for (i, (name, value)) in self.query.iter().enumerate() {
            write!(f, "{}{}={}", if i == 0 { '?' } else { '&' }, encode(name), encode(value))?;
        }
        Ok(())
    }
}

/// Chyby formulára s preloženými textami (Spring `BindingResult` + `<form:errors>`).
#[derive(Debug, Clone, Default)]
pub struct FieldErrors {
    errors: Vec<(&'static str, String)>,
}

impl FieldErrors {
    /// Preloží porušené pravidlá na texty.
    pub fn resolve(violations: &[Violation], messages: &Messages) -> Self {
        Self { errors: violations.iter().map(|v| (v.path, messages.get(v.key))).collect() }
    }

    /// Bez chýb?
    pub fn is_empty(&self) -> bool {
        self.errors.is_empty()
    }

    /// `<form:errors path="…" cssClass="field-error"/>` – prázdne, ak pole nemá chybu.
    pub fn html(&self, path: &str) -> String {
        let texts: Vec<String> =
            self.errors.iter().filter(|(p, _)| *p == path).map(|(_, text)| escape_html(text)).collect();
        if texts.is_empty() {
            return String::new();
        }
        format!(r#"<span id="{path}.errors" class="field-error">{}</span>"#, texts.join("<br/>"))
    }

    /// `<form:errors path="*" cssClass="field-error" element="div"/>` – všetky chyby formulára `form_name`.
    pub fn all_html(&self, form_name: &str) -> String {
        if self.errors.is_empty() {
            return String::new();
        }
        let texts: Vec<String> = self.errors.iter().map(|(_, text)| escape_html(text)).collect();
        format!(r#"<div id="{form_name}.errors" class="field-error">{}</div>"#, texts.join("<br/>"))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::blacklist::forms::{INVALID, REQUIRED};

    fn tags(privileges: &[Privilege]) -> Tags {
        Tags::new(Arc::new(Messages::load()), privileges.iter().copied().collect())
    }

    /// URL sa skladajú ako Java tagy: {ctx}{base}/{segment}?parametre (viewPage/action už sú v ceste).
    #[test]
    fn should_build_portlet_urls_like_java_tags() {
        let tags = tags(&[]);
        assert_eq!(tags.render("search").to_string(), "/crm-rs/blacklist/search");
        assert_eq!(tags.action("changeAction").param("detailId", 7).to_string(), "/crm-rs/blacklist/changeAction?detailId=7");
        assert_eq!(
            tags.resource("removeIdentAction").param("detailId", 1).param("identId", 2).to_string(),
            "/crm-rs/blacklist/resource/removeIdentAction?detailId=1&identId=2"
        );
    }

    /// Parametre sa kódujú ako Java URLEncoder (medzera = +, diakritika UTF-8).
    #[test]
    fn should_encode_params_like_url_encoder() {
        let url = tags(&[]).render("x").param("q", "Mária Príkladová&x=1").to_string();
        assert_eq!(url, "/crm-rs/blacklist/x?q=M%C3%A1ria+Pr%C3%ADkladov%C3%A1%26x%3D1");
        assert_eq!(encode("/crm-rs/blacklist/search"), "%2Fcrm-rs%2Fblacklist%2Fsearch");
    }

    /// ACL tagy podľa JSP mena privilégia; neznáme meno = nemá (chyba v šablóne sa zaloguje).
    #[test]
    fn should_check_privileges_by_jsp_name() {
        let tags = tags(&[Privilege::BlacklistView]);
        assert!(tags.can("BLACKLIST_VIEW"));
        assert!(!tags.can("BLACKLIST_MODIFY"));
        assert!(!tags.can("NEZNAME"));
        assert!(tags.page_allowed(&None));
        assert!(!tags.page_allowed(&Some("BLACKLIST_MODIFY")));
        assert_eq!(tags.page_denied(&Some("X")), "Na zobrazenie tejto časti nemáte privilégium X.");
    }

    /// Kľúč s prefixom enumu a s argumentom.
    #[test]
    fn should_resolve_messages_with_prefix_and_argument() {
        let tags = tags(&[]);
        assert_eq!(tags.msg_of("blacklist.state.", "ACTIVE"), "Aktívny");
        assert_eq!(tags.msg_with("blacklist.result.count", 3), "Počet záznamov: 3");
    }

    /// Chyby ako Spring form:errors: span s id {path}.errors, viac chýb cez <br/>; path="*" ako div.
    #[test]
    fn should_render_errors_like_spring_form_errors() {
        let messages = Messages::load();
        let violations = [
            Violation { path: "firstName", key: REQUIRED },
            Violation { path: "firstName", key: INVALID },
        ];
        let errors = FieldErrors::resolve(&violations, &messages);
        assert_eq!(errors.html("firstName"),
            r#"<span id="firstName.errors" class="field-error">Povinné pole.<br/>Neplatná hodnota.</span>"#);
        assert_eq!(errors.html("lastName"), "");
        assert_eq!(errors.all_html("searchCriteria"),
            r#"<div id="searchCriteria.errors" class="field-error">Povinné pole.<br/>Neplatná hodnota.</div>"#);
        assert_eq!(FieldErrors::default().all_html("x"), "");
    }

    /// Text chyby sa escapuje (HTML z helpera ide do šablóny cez |safe).
    #[test]
    fn should_escape_html_when_error_text_has_markup() {
        let messages = Messages::from_sources(&[r#"k=<b>&"'"#]);
        let errors = FieldErrors::resolve(&[Violation { path: "p", key: "k" }], &messages);
        assert!(errors.html("p").contains("&lt;b&gt;&amp;&quot;&#39;"));
    }
}
