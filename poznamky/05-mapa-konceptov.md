# Mapa: Liferay / Portlet koncept → čím ho nahradiť

Všeobecná tabuľka (platí pre Liferay 6.2 + Spring Portlet MVC). Stĺpec „V legacy CRM“ hovorí,
či a kde sa to v našom projekte vyskytuje (viď [03](03-legacy-portlety.md)).

| Koncept | Čo to je | V legacy CRM | Náhrada (Spring MVC / Spring Boot) |
|---|---|---|---|
| Portlet (`portlet.xml`) | samostatná „aplikácia“ v kúsku stránky | 13 portletov | skupina controllerov pod jedným URL prefixom (`/blacklist/**`) |
| Stránka s viacerými portletmi | portál skladá stránku z portletov | áno (hlavička, kontext zákazníka, obsah) | layout šablóna + fragmenty (JSP include / Thymeleaf fragment / SPA komponenty) |
| `DispatcherPortlet` + `<portlet>-portlet.xml` | Spring kontext na portlet | áno, vlastné podtriedy | jeden `DispatcherServlet` (v Boote automaticky) |
| `@RenderMapping` | GET vykreslenie | 174× | `@GetMapping` |
| `@ActionMapping` | POST bez výstupu, potom render | 347× | `@PostMapping` + redirect (PRG) |
| `@ResourceMapping` | AJAX/JSON/download | 598× | `@GetMapping`/`@PostMapping` vracajúci JSON (`@RestController`) |
| Render parametre (`page=...`) | stav navigácie v URL portálu | dashboard/order dispatch | normálne cesty `/dashboard/{page}` a path variables |
| `@SessionAttributes` | model držaný medzi fázami | 101× | funguje aj v servlet MVC; pri REST radšej stateless + ID |
| `PortletSession` PORTLET/APPLICATION scope | session per portlet / zdieľaná | 51× | `HttpSession` (+ Spring Session/Redis pre cluster), prefixy kľúčov |
| `PortletURL`, `<portlet:actionURL>`, `LiferayPortletURL` | URL do portálu so zakódovaným portlet ID | skryté vo vlastných `portal:*URL` tagoch | `UriComponentsBuilder`, `<c:url>`, `@{...}` v Thymeleaf, alebo len REST cesty |
| Friendly URL mapper + routes XML | pekné URL pre portlet | 5 portletov | priamo `@GetMapping("/{cuRefNo}/...")` |
| `PortalUtil.getHttpServletRequest` | dostať sa k servlet requestu | 92× | netreba – request máš priamo |
| `ThemeDisplay` | info o používateľovi, stránke, ceste k theme | 6 Java súborov, JSP len cesta k obrázkom | vlastný request-scoped bean / `@ModelAttribute` v `@ControllerAdvice` |
| `LanguageUtil`, `<liferay-ui:message>` | preklady | málo; projekt má vlastný `common:message` + DB `MessageSource` | Spring `MessageSource` (+ vlastná implementácia nad DB/CMS) |
| Liferay permissions (`PermissionChecker`, role) | autorizácia | **nepoužíva sa** (vlastné ACL) | Spring Security (`@PreAuthorize`, vlastný `PermissionEvaluator`) nad existujúcim ACL |
| Service Builder (`service.xml`, `*LocalServiceUtil`) | generovaná perzistencia | **nepoužíva sa** (len v startup hooku na nastavenie portálu) | Spring Data JPA / existujúce služby |
| Hook: `portal.properties`, startup action | zásah do portálu pri štarte | vytvára site, stránky, CMS obsah | odpadá (nie je portál); CMS obsah → externý CMS / statické |
| Hook: `custom-jsp-dir` | prepis JSP portálu | chybové stránky, meta tagy | `@ControllerAdvice` + error stránky Spring Boot |
| Theme (Velocity, separátny WAR) | vzhľad + layout | áno | statické CSS/JS v aplikácii + layout šablóna |
| Liferay CMS (Journal, AssetEntry) | webový obsah | okrajovo (prázdny košík, popisy produktov) | externý CMS / statické súbory |
| Portlet filter (napr. obmedzenie paralelných renderov) | filter na fázy portletu | 1 | servlet `Filter` / `HandlerInterceptor` (ak je ešte potrebný) |
| `HandlerInterceptor` (portlet) | pred/po spracovaní | 18 | `HandlerInterceptor` (servlet) – logika väčšinou prenosná |
| JBoss modul závislosti | `jboss-deployment-structure.xml` | áno | Spring Boot fat jar / WAR; závislosti v POM |
