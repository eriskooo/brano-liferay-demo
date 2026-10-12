# Migrácia portletu blacklist: pred → po

„Pred“ je skutočná stará appka v [`legacy/`](../legacy) (Liferay 6.2, Java 8, Spring Portlet MVC), „po“ je
[`crm-web/`](../crm-web) (Spring Boot 4.1.1, Java 21). Obe bežia súčasne (`docker/`), takže každý bod sa dá
porovnať aj v prehliadači: starý blacklist na :8080, nový na :8000. Skratky viď `poznamky/00-konvencie.md`.

## 1. Portlet → skupina controllerov
- **Pred:** `portlet.xml` + `blacklist-portlet.xml` (vlastný Spring kontext) + `DispatcherPortletCustom`;
  controller sa vyberá render parametrom `viewPage`, metóda parametrom `action`/`render`
  (`legacy/customercare-portlets/.../blacklist/BlacklistSearchController.java`, `BlacklistDetailController.java`).
- **Po:** `@LegacyPortlet("/blacklist")` + `@RequestMapping("/blacklist")`, jeden `DispatcherServlet`
  (`crm-web/.../blacklist/BlacklistSearchController.java`, `web/portlet/LegacyPortletInterceptor.java`).
- **Prečo:** bez portálu niet portletov ani ich kontextov; URL je adresa stránky, nie parametre portletu.

## 2. Fázy portletu → HTTP
| Legacy | Nové | Prečo |
|---|---|---|
| render (`@RenderMapping`) | `GET` | čistá adresa, bookmark, F5 bez opakovania |
| action (`@ActionMapping`, render parameter) | `POST` + `redirect:` (PRG) | action nemala výstup; v servlete to robí redirect |
| resource (`@ResourceMapping`, `ModelAndView` fragment) | AJAX endpoint `/resource/…` (fragment / JSON) | JS sa nemení, mení sa len URL |
| validačné chyby cez „implicit model“ v session | chyby sa vykreslia priamo (bez redirectu) | servlet MVC implicit model nemá |

## 3. URL tagy
- **Pred:** `portal:actionURL` pretypuje na `LiferayPortletURL` (`legacy/frontend-base-customization/.../tag/PortletUrlTagSupport.java`).
- **Po:** ten istý tag a atribúty, URL = `{ctx}{base}/{action|render|viewPage}` (`crm-web/.../web/tag/PortletUrlTag.java`).
- **Prečo:** ~7 000 použití v JSP sa nemení; skrytá Liferay závislosť zmizne na jednom mieste.

## 4. Session fasáda a identita
- **Pred:** singleton `SessionManagerCustom` – request z `RequestContextHolder`, identita zo SSO cez API JAR
  a service locator, ostatné v `PortletSession` + Redis (duálna session, flag)
  (`legacy/frontend-base-customization/.../session/`).
- **Po:** `CrmSession` ako parameter controllera, identita zo SSO cez REST, zdieľané kľúče len v Redise
  v **rovnakom JSON** (`crm-web/.../session/`).
- **Prečo:** controllery sa nemenia (rovnaký kontrakt); stará a nová appka si odovzdávajú stav cez Redis.
  Pri súbehu musí mať stará appka flag `REDIS`, inak nevidí zápisy novej (`docker/smoke-migration.sh`, kroky 6–7).

## 5. Formuláre a validácia
- **Pred:** vlastné `Validator` triedy volané v controlleri (`SearchCriteriaValidator`, `BlacklistCreateValidator`).
- **Po:** Bean Validation na formulári (`@NotBlank`, `@NotNull`, `@Past`, krížové pravidlá ako `@AssertTrue` metódy) + `@Valid`;
  povolenú podmienku pre dôvod overuje controller cez službu (`crm-web/.../blacklist/BlacklistCreateController.java`).
- **Prečo:** menej kódu, pravidlá pri poliach formulára; business pravidlo (povolená podmienka) ostáva v službe.

## 6. ACL
- **Pred:** privilégium portletu v `portlet.xml` + `checkPrivilege` v `BaseCrmController` + tagy `aclFilter`.
- **Po:** `@RequiresPrivilege` na metóde/triede, kontrola v interceptore; tagy rovnaké.
- **Prečo:** security sa podľa podkladov nemení – len miesto, kde sa kontroluje.

## 7. Kompozícia stránky
- **Pred:** theme (Velocity) + runtime portlet `header` (operátor, zákazník, hlášky).
- **Po:** `layout/header.jspf` + `LayoutModelInterceptor`.
- **Prečo:** bez portálu stránku neskladá portál; hlavička je fragment šablóny.

## 8. Chybové stavy
- **Pred:** `SimpleMappingExceptionResolverCustom` + hooked `status.jsp`; render bez SSO vykreslí len odkaz.
- **Po:** `GlobalExceptionHandler` (403/404/503) + `error.jsp`; bez SSO redirect na login (servlet to vie).

## 9. Nasadenie počas migrácie (strangler)
- nginx presmeruje `/web/crm/blacklist*` na `/crm/blacklist/search`; dashboard ostáva v Liferay.
- Nová appka má vlastnú session cookie (`CRM_SESSION`, `Path=/crm`) – inak prepíše `JSESSIONID` Liferay.
- Návrat späť (rollback) = odstrániť presmerovanie v nginx; dáta sa nemigrujú, session je zdieľaná.

## 10. Čo demo zámerne nerieši
- **Backend:** stará appka má backend v JVM Liferay; nová má vlastnú in-memory kópiu. Reálne by obe volali ten istý
  backend (REST adaptér alebo spoločná DB) – to je samostatné rozhodnutie (riziko č. 2 v `docs/ANALYZA.md`).
- Dashboard, Spring Security, cluster.

## Mapovacia tabuľka

| Liferay / portlet | Spring Boot (demo) |
|---|---|
| `portlet.xml`, `<portlet>-portlet.xml`, `DispatcherPortlet` | `@LegacyPortlet` + controllery, jeden `DispatcherServlet` |
| `@RenderMapping` / `@ActionMapping` / `@ResourceMapping` | `@GetMapping` / `@PostMapping` + redirect / AJAX endpoint |
| render parametre (`viewPage`, `action`) | cesta URL |
| `LiferayPortletURL` v tagoch | ten istý tag, servletová URL |
| `PortletSession` (portlet/application scope) | `HttpSession` (lokálne) + Redis (zdieľané kľúče) |
| `SessionManagerCustom` + SSO API JAR | `CrmSession` + SSO REST |
| vlastné `Validator` | Bean Validation |
| theme + portlet hlavičky | `header.jspf` + interceptor |
| exception resolver + hooked JSP | `@ControllerAdvice` + `error.jsp` |
| startup hook (site, stránky) | odpadá – stránky sú URL |
