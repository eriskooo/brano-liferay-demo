# Migrace portletu blacklist: před → po

„Před“ je skutečná stará aplikace v [`legacy/`](../legacy) (Liferay 6.2, Java 8, Spring Portlet MVC), „po“ je
[`crm-web/`](../crm-web) (Spring Boot 4.1.1, Java 21). Obě běží současně (`docker/`), takže každý bod lze
porovnat i v prohlížeči: starý blacklist na :8080, nový na :8000. Zkratky viz `poznamky/00-konvencie.md`.

## 1. Portlet → skupina controllerů
- **Před:** `portlet.xml` + `blacklist-portlet.xml` (vlastní Spring kontext) + `DispatcherPortletCustom`;
  controller se vybírá render parametrem `viewPage`, metoda parametrem `action`/`render`
  (`legacy/customercare-portlets/.../blacklist/BlacklistSearchController.java`, `BlacklistDetailController.java`).
- **Po:** `@LegacyPortlet("/blacklist")` + `@RequestMapping("/blacklist")`, jeden `DispatcherServlet`
  (`crm-web/.../blacklist/BlacklistSearchController.java`, `web/portlet/LegacyPortletInterceptor.java`).
- **Proč:** bez portálu nejsou portlety ani jejich kontexty; URL je adresa stránky, ne parametry portletu.

## 2. Fáze portletu → HTTP
| Legacy | Nové | Proč |
|---|---|---|
| render (`@RenderMapping`) | `GET` | čistá adresa, záložka, F5 bez opakování |
| action (`@ActionMapping`, render parameter) | `POST` + `redirect:` (PRG) | action neměla výstup; v servletu to dělá redirect |
| resource (`@ResourceMapping`, `ModelAndView` fragment) | AJAX endpoint `/resource/…` (fragment / JSON) | JS se nemění, mění se jen URL |
| validační chyby přes „implicit model“ v session | chyby se vykreslí přímo (bez redirectu) | servletové MVC implicit model nemá |

## 3. URL tagy
- **Před:** `portal:actionURL` přetypuje na `LiferayPortletURL` (`legacy/frontend-base-customization/.../tag/PortletUrlTagSupport.java`).
- **Po:** tentýž tag a atributy, URL = `{ctx}{base}/{action|render|viewPage}` (`crm-web/.../web/tag/PortletUrlTag.java`).
- **Proč:** ~7 000 použití v JSP se nemění; skrytá Liferay závislost zmizí na jednom místě.

## 4. Session fasáda a identita
- **Před:** singleton `SessionManagerCustom` – request z `RequestContextHolder`, identita ze SSO přes API JAR
  a service locator, ostatní v `PortletSession` + Redis (duální session, flag)
  (`legacy/frontend-base-customization/.../session/`).
- **Po:** `CrmSession` jako parametr controlleru, identita ze SSO přes REST, sdílené klíče jen v Redisu
  ve **stejném JSON** (`crm-web/.../session/`).
- **Proč:** controllery se nemění (stejný kontrakt); stará a nová aplikace si předávají stav přes Redis.
  Při souběhu musí mít stará aplikace flag `REDIS`, jinak nevidí zápisy nové (`docker/smoke-migration.sh`, kroky 6–7).

## 5. Formuláře a validace
- **Před:** vlastní třídy `Validator` volané v controlleru (`SearchCriteriaValidator`, `BlacklistCreateValidator`).
- **Po:** Bean Validation na formuláři (`@NotBlank`, `@NotNull`, `@Past`, křížová pravidla jako metody `@AssertTrue`) + `@Valid`;
  povolenou podmínku pro důvod ověřuje controller přes službu (`crm-web/.../blacklist/BlacklistCreateController.java`).
- **Proč:** méně kódu, pravidla u polí formuláře; business pravidlo (povolená podmínka) zůstává ve službě.

## 6. ACL
- **Před:** privilegium portletu v `portlet.xml` + `checkPrivilege` v `BaseCrmController` + tagy `aclFilter`.
- **Po:** `@RequiresPrivilege` na metodě/třídě, kontrola v interceptoru; tagy stejné.
- **Proč:** security se podle podkladů nemění – jen místo, kde se kontroluje.

## 7. Kompozice stránky
- **Před:** theme (Velocity) + runtime portlet `header` (operátor, zákazník, hlášky).
- **Po:** `layout/header.jspf` + `LayoutModelInterceptor`.
- **Proč:** bez portálu stránku neskládá portál; hlavička je fragment šablony.

## 8. Chybové stavy
- **Před:** `SimpleMappingExceptionResolverCustom` + hooked `status.jsp`; render bez SSO vykreslí jen odkaz.
- **Po:** `GlobalExceptionHandler` (403/404/503) + `error.jsp`; bez SSO redirect na login (servlet to umí).

## 9. Nasazení během migrace (strangler)
- nginx přesměruje `/web/crm/blacklist*` na `/crm/blacklist/search`; dashboard zůstává v Liferay.
- Nová aplikace má vlastní session cookie (`CRM_SESSION`, `Path=/crm`) – jinak přepíše `JSESSIONID` Liferay.
- Návrat zpět (rollback) = odstranit přesměrování v nginx; data se nemigrují, session je sdílená.

## 10. Co demo záměrně neřeší
- **Backend:** stará aplikace má backend v JVM Liferay; nová má vlastní in-memory kopii. Reálně by obě volaly tentýž
  backend (REST adaptér nebo společná DB) – to je samostatné rozhodnutí (riziko č. 2 v `docs/ANALYZA.md`).
- Dashboard, Spring Security, cluster.

## Mapovací tabulka

| Liferay / portlet | Spring Boot (demo) |
|---|---|
| `portlet.xml`, `<portlet>-portlet.xml`, `DispatcherPortlet` | `@LegacyPortlet` + controllery, jeden `DispatcherServlet` |
| `@RenderMapping` / `@ActionMapping` / `@ResourceMapping` | `@GetMapping` / `@PostMapping` + redirect / AJAX endpoint |
| render parametry (`viewPage`, `action`) | cesta URL |
| `LiferayPortletURL` v tazích | tentýž tag, servletová URL |
| `PortletSession` (portlet/application scope) | `HttpSession` (lokálně) + Redis (sdílené klíče) |
| `SessionManagerCustom` + SSO API JAR | `CrmSession` + SSO REST |
| vlastní `Validator` | Bean Validation |
| theme + portletové hlavičky | `header.jspf` + interceptor |
| exception resolver + hooked JSP | `@ControllerAdvice` + `error.jsp` |
| startup hook (site, stránky) | odpadá – stránky jsou URL |
