# Předchozí PoC: modul `customercare-web` (Spring MVC bez Liferay)

Cesty: `CW` = `$FE/customercare-web/target`, `CARGO` = `CW/cargo/configurations/tomcat9x/webapps/customercare-web`.
**Zdrojáky (`src/`) v podkladech nejsou** – vše je odvozeno z build výstupů (XML, `.class` přes `javap`).

## Stav podle `$FE/customercare-web/MIGRATION_PROGRESS.md` (tvrzení souboru, neověřeno)
- „100 %“ JSP přeneseno (1242 vs. 1241 legacy), 43 Spring MVC controllerů, 522 handlerů,
  pokrytí routami 152/152.
- Liferay odstraněn z runtime; tagy `portal:`, `common:`, `eshop:`, `cr:`, `cri:` nahrazeny
  kompatibilní vrstvou; **všechny routy běží na mock datech, napojení backendu je „další fáze“**.
- ⚠ „100 %“ znamená „JSP se zkompiluje a má routu“, **ne funkční paritu** – viz níže.

## Tři kopie build výstupu (nejsou shodné!) ✔
1. `CW/classes` – nejnovější compile (100 zdrojových souborů podle `maven-status`).
2. `CW/customercare-web` – rozbalený WAR, **neúplný** (21 jarů, bez `web.xml` a JSP) ✔.
   Tady chybí backend JAR uvedený v zadání.
3. `CARGO` – nasazená kopie v Tomcatu: 1244 JSP, 186 jarů, `web.xml` ✔; **backend JAR tu je
   a je celý** (`unzip -t` OK) ✔. Její `classes` jsou však **starší** (např. chybí
   `TariffDeactivationWebController`) ✔.
⇒ Archiv není „poškozený za JARem“ – jen jedna z kopií je neúplná. Pro analýzu stačí `CARGO` + `CW/classes`.

## Jak PoC nahrazuje Liferay
| Liferay věc | PoC náhrada | Poznámka |
|---|---|---|
| Portlet kontejner + `DispatcherPortlet` | jeden `DispatcherServlet` na `/` (`web.xml`, `webmvc-servlet.xml`) | |
| `@RenderMapping`/`@ActionMapping` | `@GetMapping`/`@PostMapping` v `*WebController` | 213 controllerů sloučených do 43 |
| `<portal:actionURL>` / `renderURL` / `resourceURL` | `GenericUrlTag` – skládá `contextPath + URI + ?k=v` | stejné JSP, jiná implementace tagu |
| `<portlet:*>`, `aui:`, `liferay-ui:`, `liferay-util:` | stub TLD → `CompatBodyTag` / `CompatNoOpTag` | tagy „nic nedělají“ |
| `liferay-theme:defineObjects` | `DefineObjectsTag` → objekt s jedinou metodou `getPathThemeImages()` | stačí, protože JSP nic jiného nevolají |
| `<common:message>` (i18n) | `MessageTag` **bez MessageSource** – vypíše klíč/default ✔ (bytecode) | ⚠ překlady nefungují |
| Session manager + SSO portál | **nenahrazeno**, jen fake login do `HttpSession` | největší mezera |
| Backend služby (in-process) | `StubServiceBeanFactory` = JDK proxy no-op; 4 demo impl. (blacklist, dokumenty, historie požadavků, důvody deaktivace) | `datasource-stub.xml` |
| Liferay výjimky | minimální shim třídy `PortalException`/`SystemException` v PoC | aby šel compile legacy tříd |
| Theme | statické `/js`, `/css`, `/img` (`mvc:resources`) | |

- Přepínání stub ↔ produkce: `applicationContext.xml` (stub) vs. `applicationContext-production.xml`
  (importuje backend kontexty z jarů).
- PoC stále tahá `portlet-api` a `spring-webmvc-portlet` na classpath – JSP používají SpEL konstanty
  z legacy controllerů a `PortletRequest.RESOURCE_PHASE`. ⇒ Liferay je pryč, **portlet API ne**.
- Kompilace: vyloučené balíky `autogen` / `legacygenerated`.

## Poučení pro reálnou migraci
- Měřit pokrok počtem JSP je zavádějící. Měřit je třeba **scénáře, které jdou end-to-end s reálným backendem**.
- Chyba z logu (`BindingResult … tariffDeactivationModel`) = controller nevložil form objekt do modelu.
  V portletech ho dodávala `@ModelAttribute` metoda ve třídě, při slučování do větších
  `*WebController`ů se to ztratilo. Viz [07-pasce.md](07-pasce.md).
- Stub všeho přes `FactoryBean` + proxy je rychlý způsob, jak aplikaci „nastartovat“.
  Vrací však `null`, takže se chyby přesunou do JSP.
