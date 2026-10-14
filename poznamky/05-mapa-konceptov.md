# Mapa: Liferay / Portlet koncept → čím ho nahradit

Obecná tabulka (platí pro Liferay 6.2 + Spring Portlet MVC). Sloupec „V legacy CRM“ říká,
zda a kde se to v našem projektu vyskytuje (viz [03](03-legacy-portlety.md)).

| Koncept | Co to je | V legacy CRM | Náhrada (Spring MVC / Spring Boot) |
|---|---|---|---|
| Portlet (`portlet.xml`) | samostatná „aplikace“ v kousku stránky | 13 portletů | skupina controllerů pod jedním URL prefixem (`/blacklist/**`) |
| Stránka s více portlety | portál skládá stránku z portletů | ano (hlavička, kontext zákazníka, obsah) | layout šablona + fragmenty (JSP include / Thymeleaf fragment / SPA komponenty) |
| `DispatcherPortlet` + `<portlet>-portlet.xml` | Spring kontext na portlet | ano, vlastní podtřídy | jeden `DispatcherServlet` (v Bootu automaticky) |
| `@RenderMapping` | GET vykreslení | 174× | `@GetMapping` |
| `@ActionMapping` | POST bez výstupu, potom render | 347× | `@PostMapping` + redirect (PRG) |
| `@ResourceMapping` | AJAX/JSON/download | 598× | `@GetMapping`/`@PostMapping` vracející JSON (`@RestController`) |
| Render parametry (`page=...`) | stav navigace v URL portálu | dashboard/order dispatch | normální cesty `/dashboard/{page}` a path variables |
| `@SessionAttributes` | model držený mezi fázemi | 101× | funguje i v servlet MVC; u REST raději stateless + ID |
| `PortletSession` PORTLET/APPLICATION scope | session per portlet / sdílená | 51× | `HttpSession` (+ Spring Session/Redis pro cluster), prefixy klíčů |
| `PortletURL`, `<portlet:actionURL>`, `LiferayPortletURL` | URL do portálu se zakódovaným portlet ID | skryté ve vlastních `portal:*URL` tazích | `UriComponentsBuilder`, `<c:url>`, `@{...}` v Thymeleaf, nebo jen REST cesty |
| Friendly URL mapper + routes XML | hezké URL pro portlet | 5 portletů | přímo `@GetMapping("/{cuRefNo}/...")` |
| `PortalUtil.getHttpServletRequest` | dostat se k servlet requestu | 92× | není potřeba – request máš přímo |
| `ThemeDisplay` | info o uživateli, stránce, cestě k theme | 6 Java souborů, JSP jen cesta k obrázkům | vlastní request-scoped bean / `@ModelAttribute` v `@ControllerAdvice` |
| `LanguageUtil`, `<liferay-ui:message>` | překlady | málo; projekt má vlastní `common:message` + DB `MessageSource` | Spring `MessageSource` (+ vlastní implementace nad DB/CMS) |
| Liferay permissions (`PermissionChecker`, role) | autorizace | **nepoužívá se** (vlastní ACL) | Spring Security (`@PreAuthorize`, vlastní `PermissionEvaluator`) nad existujícím ACL |
| Service Builder (`service.xml`, `*LocalServiceUtil`) | generovaná perzistence | **nepoužívá se** (jen ve startup hooku pro nastavení portálu) | Spring Data JPA / existující služby |
| Hook: `portal.properties`, startup action | zásah do portálu při startu | vytváří site, stránky, CMS obsah | odpadá (není portál); CMS obsah → externí CMS / statické |
| Hook: `custom-jsp-dir` | přepis JSP portálu | chybové stránky, meta tagy | `@ControllerAdvice` + error stránky Spring Boot |
| Theme (Velocity, samostatný WAR) | vzhled + layout | ano | statické CSS/JS v aplikaci + layout šablona |
| Liferay CMS (Journal, AssetEntry) | webový obsah | okrajově (prázdný košík, popisy produktů) | externí CMS / statické soubory |
| Portlet filter (např. omezení paralelních renderů) | filtr na fáze portletu | 1 | servlet `Filter` / `HandlerInterceptor` (pokud je ještě potřeba) |
| `HandlerInterceptor` (portlet) | před/po zpracování | 18 | `HandlerInterceptor` (servlet) – logika většinou přenositelná |
| JBoss modul závislosti | `jboss-deployment-structure.xml` | ano | Spring Boot fat jar / WAR; závislosti v POM |
