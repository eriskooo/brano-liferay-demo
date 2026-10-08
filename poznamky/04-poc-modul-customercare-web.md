# Predchádzajúci PoC: modul `customercare-web` (Spring MVC bez Liferay)

Cesty: `CW` = `$FE/customercare-web/target`, `CARGO` = `CW/cargo/configurations/tomcat9x/webapps/customercare-web`.
**Zdrojáky (`src/`) v podkladoch nie sú** – všetko je odvodené z build výstupov (XML, `.class` cez `javap`).

## Stav podľa `$FE/customercare-web/MIGRATION_PROGRESS.md` (tvrdenia súboru, neoverené)
- „100 %“ JSP prenesených (1242 vs. 1241 legacy), 43 Spring MVC controllerov, 522 handlerov,
  pokrytie routami 152/152.
- Liferay odstránený z runtime; tagy `portal:`, `common:`, `eshop:`, `cr:`, `cri:` nahradené
  kompatibilnou vrstvou; **všetky routy bežia na mock dátach, napojenie backendu je „ďalšia fáza“**.
- ⚠ „100 %“ znamená „JSP sa skompiluje a má routu“, **nie funkčnú paritu** – pozri nižšie.

## Tri kópie build výstupu (nie sú zhodné!) ✔
1. `CW/classes` – najnovší compile (100 zdrojových súborov podľa `maven-status`).
2. `CW/customercare-web` – rozbalený WAR, **neúplný** (21 jarov, bez `web.xml` a JSP) ✔.
   Tu chýba backend JAR uvedený v zadaní.
3. `CARGO` – nasadená kópia v Tomcate: 1244 JSP, 186 jarov, `web.xml` ✔; **backend JAR je tu
   a je celý** (`unzip -t` OK) ✔. Jej `classes` sú však **staršie** (napr. chýba
   `TariffDeactivationWebController`) ✔.
⇒ Archív nie je „poškodený za JARom“ – len jedna z kópií je neúplná. Pre analýzu stačí `CARGO` + `CW/classes`.

## Ako PoC nahrádza Liferay
| Liferay vec | PoC náhrada | Poznámka |
|---|---|---|
| Portlet kontajner + `DispatcherPortlet` | jeden `DispatcherServlet` na `/` (`web.xml`, `webmvc-servlet.xml`) | |
| `@RenderMapping`/`@ActionMapping` | `@GetMapping`/`@PostMapping` v `*WebController` | 213 controllerov zlúčených do 43 |
| `<portal:actionURL>` / `renderURL` / `resourceURL` | `GenericUrlTag` – skladá `contextPath + URI + ?k=v` | rovnaké JSP, iná implementácia tagu |
| `<portlet:*>`, `aui:`, `liferay-ui:`, `liferay-util:` | stub TLD → `CompatBodyTag` / `CompatNoOpTag` | tagy „nič nerobia“ |
| `liferay-theme:defineObjects` | `DefineObjectsTag` → objekt s jedinou metódou `getPathThemeImages()` | stačí, lebo JSP nič iné nevolajú |
| `<common:message>` (i18n) | `MessageTag` **bez MessageSource** – vypíše kľúč/default ✔ (bytecode) | ⚠ preklady nefungujú |
| Session manager + SSO portál | **nenahradené**, len fake login do `HttpSession` | najväčšia diera |
| Backend služby (in-process) | `StubServiceBeanFactory` = JDK proxy no-op; 4 demo impl. (blacklist, dokumenty, história požiadaviek, dôvody deaktivácie) | `datasource-stub.xml` |
| Liferay výnimky | minimálne shim triedy `PortalException`/`SystemException` v PoC | aby šiel compile legacy tried |
| Theme | statické `/js`, `/css`, `/img` (`mvc:resources`) | |

- Prepínanie stub ↔ produkcia: `applicationContext.xml` (stub) vs. `applicationContext-production.xml`
  (importuje backend kontexty z jarov).
- PoC stále ťahá `portlet-api` a `spring-webmvc-portlet` na classpath – JSP používajú SpEL konštanty
  z legacy controllerov a `PortletRequest.RESOURCE_PHASE`. ⇒ Liferay je preč, **portlet API nie**.
- Kompilácia: vylúčené balíky `autogen` / `legacygenerated`.

## Ponaučenia pre reálnu migráciu
- Merať pokrok počtom JSP je zavádzajúce. Merať treba **scenáre, ktoré idú end-to-end s reálnym backendom**.
- Chyba z logu (`BindingResult … tariffDeactivationModel`) = controller nevložil form objekt do modelu.
  V portletoch ho dodával `@ModelAttribute` metóda v triede, pri zlučovaní do väčších
  `*WebController`ov sa to stratilo. Pozri [07-pasce.md](07-pasce.md).
- Stub všetkého cez `FactoryBean` + proxy je rýchly spôsob, ako aplikáciu „naštartovať“.
  Vracia však `null`, takže chyby sa presunú do JSP.
