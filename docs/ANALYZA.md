# Analýza: legacy CRM na Liferay 6.2 → Spring Boot

> Anonymizovaná analýza podkladů v `input/` (git-ignorované). Zkratky (`$FE`, `<pkg>`, „SSO portál“…)
> jsou vysvětleny v [`poznamky/00-konvencie.md`](../poznamky/00-konvencie.md).
> Značky: ✔ = ověřeno vlastním příkazem (grep/find/unzip/javap) · ⚠ neověřeno = tvrzení podkladů
> nebo odvození, které nebylo možné ověřit (chybějící zdroje, nelze spustit).

Cesty: `CP` = `$FE/customercare-portlets/src/main`, `J` = `CP/java/<pkg>/crm/frontend`,
`FBC` = `$FE/frontend-base-customization/src/main/java/<pkg>/crm/frontend/base`,
`CARGO` = `$FE/customercare-web/target/cargo/configurations/tomcat9x/webapps/customercare-web`.

---

## 1. O jaký projekt jde

**Legacy CRM pro zákaznickou podporu** telco operátora. Interní operátoři ho používají pro práci se
zákazníkem: dashboard zákazníka, objednávky a změny služeb, vyúčtování, požadavky zákazníka,
scoring a blacklist.

- Frontend je **portletová aplikace na Liferay 6.2** (root POM: `liferay.version=6.2.5`) běžící na JBossu.
  Stack: Java 8, Spring 4.3.6, Hibernate 4.2, Oracle 19 (`input/pom.xml`; architektura ze slajdu 14 prezentace).
- Backend a integrace (REST/SOAP vůči integrační vrstvě, message broker, SSO portál) jsou **ve stejném JVM
  a Spring kontextu**: `CP/resources/context-jboss.xml` importuje backendové kontexty (DAO, služby, transakce,
  WS/REST klienty) z JARů.
- Zdrojové kódy backendu v podkladech **nejsou**. Je k dispozici jen frontendový modul `$FE` a root POM, ve kterém jsou
  sesterské moduly (`db-layer`, `backend`, `integration`, …) uvedeny, ale chybí. Legacy projekt
  **nelze sestavit** (soukromý Maven repozitář, chybějící moduly).

### Co je zadáním podle podkladů
Zdroj: prezentace `input/*-AI*.pptx` a Q&A dokument `input/*-odpovedi-komentare.*` (detail v
[`poznamky/01`](../poznamky/01-zadanie-z-podkladov.md)).

- **Odstranit Liferay** jako nepodporovanou a rizikovou závislost, s **business funkcionalitou 1:1**
  a s **technickou filozofií 1:1**. To znamená: žádné SPA, žádný přesun logiky mezi FE a BE,
  stavová aplikace (slajd 2).
- Cílový stack v podkladech: **Spring MVC + tytéž JSP na čistém Tomcatu**. Portletové tagy se
  reimplementují bez Liferay, theme se nahradí statickými soubory a backendové služby zůstávají (slajdy 4, 5, 13).
- Otevřená témata podle podkladů: duální session s Redisem (slajd 12), deeplinky a odkazy „zpět“
  („nutno rozpracovat“), CMS překlady a upgrade Javy spolu s odstraněním Liferay (komentáře klienta).
  Čísla „375+ controllerů“ a „30+ objektů v session“ ze slajdu 12 jsou ⚠ neověřená.

> **Rozpor se zadáním dema:** zadání dema požaduje *Spring Boot + REST API*, podklady požadují
> *server-side JSP bez SPA*. Návrh řešení je v kap. 6.

---

## 2. Moduly (`$FE/pom.xml`)

| Modul | Co to je | Zdroj v podkladech |
|---|---|---|
| `customercare-portlets` | legacy portletový WAR (všechny obrazovky) | ✔ plný `src/` (~1418 Java souborů, **1241 JSP** ✔, 219 tag files) |
| `frontend-base-customization` | sdílená knihovna: session fasáda, vlastní tagy, portal utils, i18n, cache | ✔ `src/` |
| `customercare-theme` | Liferay theme (Velocity šablony, LAR export obsahu) | jen předsestavený WAR |
| `customercare-web` | **předchozí PoC** „bez Liferay“ (Spring MVC, Tomcat 9 přes Cargo) | ⚠ **jen `target/`**, žádný `src/` |
| `customercare-testing` | zátěžové testy (Liferay portlet) | `src/`, není v `<modules>` |
| `one-portal-portlets` | portlet session manageru SSO portálu (Spring 3, rok 2016) | jen předsestavený WAR |

**Stav archivu:** v `$FE/customercare-web/target/customercare-web/WEB-INF/lib` je jen 21 JARů a chybí
backendový JAR. Tato kopie nemá ani `web.xml`, ani JSP. V nasazené kopii `CARGO/WEB-INF/lib` je však
**186 JARů a tentýž JAR je přítomen a neporušen** ✔ (`unzip -t`: bez chyb). Neúplná je tedy jen
jedna ze tří kopií build výstupu, nikoli celý archiv. Kopie se liší i verzí: `CARGO/WEB-INF/classes`
jsou starší než `customercare-web/target/classes` ✔ (např. `TariffDeactivationWebController` je jen v novější).

---

## 3. Architektura legacy frontendu

### 3.1 Portlety ✔ `CP/webapp/WEB-INF/portlet.xml`
13 portletů (JSR-286), všechny `instanceable=false`, `private-session-attributes=false`
(`liferay-portlet.xml`):

| Oblast | Portlety | Dispatch | JSP |
|---|---|---|---|
| Shell | `customercontext`, `header`, `login` (fake) | anotace | 4 + 13 + 1 |
| Dashboard zákazníka | `dashboard` | **vlastní `DashboardHandlerMapping` podle parametru `page`** | 529 |
| Objednávky | `order` | **vlastní `CustomScenarioHandlerMapping` (page-flow scénář/krok)** | 444 |
| Vyúčtování | `billingmanagement` | anotace | 62 |
| Požadavky zákazníka | `customerrequest`, `scoringrequest` | `CustomerRequestTypeHandlerMapping` podle `@CrType` / anotace | 28 + 1 |
| Vyhledávání/admin | `noncustomerblacklist`, `documentsearch`, `contactpersonsearch` | anotace | 5 + 4 + 3 |
| Vyřazené | `entityaccessmanagement`, `documents` | `DecommissioningDispatcherPortlet` | 24 + 11 |

(počty JSP podle adresářů `CP/webapp/WEB-INF/jsp/<oblasť>/`; `common/` dalších 112)

### 3.2 Typ portletů: Spring Portlet MVC ✔
- Portletové třídy jsou vlastní podtřídy Spring `DispatcherPortlet` (`J/portlet/DispatcherPortletCustom.java`,
  `DispatcherPortletCustomerContext.java`). Každý portlet má vlastní Spring kontext
  `CP/webapp/WEB-INF/<portlet>-portlet.xml` s view resolverem `/WEB-INF/jsp/<portlet>/`.
- Anotace ✔ (grep `CP/java`): `@Controller` 246, `@RenderMapping` 174, `@ActionMapping` 347,
  `@ResourceMapping` 598. Dále `@SessionAttributes` 101 a `@ModelAttribute` 745.
- Žádné `MVCPortlet`, `GenericPortlet` ani JSF.
- Společný base controller `J/portlet/controller/BaseCustomerCareController.java` (~1900 LOC) dědí
  z třídy externí core knihovny ⚠ (zdroj není k dispozici).

### 3.3 Použité Liferay API

| Oblast | Nález | Kde |
|---|---|---|
| **Service Builder** | **nepoužívá se** ✔ (žádný `service.xml`) | – |
| Přímé importy `com.liferay.*` | 119 Java souborů ✔ v `CP/java`, 17 v FBC | zejména `PortalUtil` (175 volání, 92× `getHttpServletRequest`) |
| `ThemeDisplay` | 6 Java souborů, v JSP téměř jen `getPathThemeImages` | `J/util/ControllerManagerCustomImpl.java`, `J/portlet/DoubleclickProtectedHandlerAdapter.java` |
| `LanguageUtil` / i18n | okrajově (jazyk/země); překlady jdou přes vlastní `<common:message>` a `MessageSource` z DB | `FBC/service/i18/MessageSourceServiceCustomImpl.java`, `CP/resources/portlet-base-context.xml` |
| **Permissions** | Liferay `PermissionChecker` jen ve startup hooku; **business autorizace = vlastní ACL** | `<common:aclPageFilterTag privilegeType=…>`, ACL služba v `DispatcherPortletCustom` |
| **Hooks** ✔ | `liferay-hook.xml`: `portal.properties` (startup event, phishing-protected session atribut) + `custom-jsp-dir` (meta tagy, chybové stránky 403/404/500) | `CP/webapp/WEB-INF/hooked/` |
| Startup hook | vytváří site, stránky, rozmisťuje portlety, Expando, Journal články (`*LocalServiceUtil` 60×) | `CP/java/<pkg>/crm/hook/ConfigurationHook.java` + `hook/activity/` |
| **Friendly URL** ✔ | 5 route souborů, mapper pro `order`, `customerrequest`, `scoringrequest` (výchozí) a `dashboard`, `billingmanagement` (vlastní) | `CP/resources/friendly/*.xml`, `J/portlet/CustomerContextFriendlyURLMapper.java` |
| Portlet URL | v Javě se `createRenderURL` přímo nevolá; URL vytvářejí **vlastní tagy** `portal:actionURL/resourceURL/renderURL`, které přetypují na `LiferayPortletURL` | `FBC/jsp/tag/portal/AbstractPortletUrlTag.java` |
| Liferay taglibs v JSP | `liferay-ui`, `aui`: 0 použití; `liferay-theme`: 29 souborů; dominují vlastní `portal:` (~7 200 použití v ~940 JSP) a `common:` (~9 500 v ~1 100 JSP) | `CP/webapp/WEB-INF/tld/` (15 TLD) |
| **Theme** | samostatný WAR (Velocity), portletový WAR ho vyžaduje (`liferay-plugin-package.properties`) | `$FE/customercare-theme` |
| CMS | okrajově: `AssetEntry` pro popisy produktů, Journal článek pro prázdný košík | `BaseCustomerCareController.java` (~ř. 961), `JournalArticleCustomTag.java` |
| Session | `PortletSession` 51× (hlavně PORTLET_SCOPE); hlavní logika ve fasádě | `FBC/service/SessionManagerCustom.java` (~780 LOC), `impl/SessionManagerCustomImpl.java` (~1750 LOC) |
| Portlet filter | omezovač paralelních renderů z knihovny SSO portálu | `portlet.xml`, `CP/resources/threadcounter.properties` |
| JBoss | `jboss-deployment-structure.xml` závisí na modulu Liferay a modulech SSO portálu | `CP/webapp/WEB-INF/` |

⚠ Čísla bez ✔ pocházejí z průzkumu kódu (grep přes subagenta) a nebyla ručně přepočítána.

### 3.4 Session a identita
- Přihlášený uživatel a část kontextu žijí v **SSO portálu**. Fasáda session manageru ho volá přes
  service locator (`SessionManagerCustomImpl`, metody `get1PSession`/`set1P*`), API je `provided`
  závislost. Pro lokální běh existuje fake (`FBC/portal/fakeservice/*Fake`).
- `getSessionManager()` se volá 414× ve 114 souborech. Je to **jediný bod**, přes který controllery
  pracují se session. Na tom staví návrh „duální session“ v prezentaci.

### 3.5 Závislosti na backendových službách
- Frontend volá backend **přímo přes Java interfacy** z JARů core knihovny a projektové customizace
  (`backend.service.*`, `integration.client.*`). Vše běží v jednom Spring kontextu; není tam
  remoting ani EJB (`CP/resources/context-jboss.xml`).
- Nejčastěji importované služby ⚠: property accessor, požadavky zákazníka, číselníky, vyhledávání
  adres, splátky, dokumenty, ceny, závazky, scoring, přenos čísla, platební brána.

### 3.6 Co říkají logy (`input/*_war*.log`) ✔
- Logy pocházejí z **PoC `customercare-web`**, ne z legacy portálu. Jde o dva starty Tomcatu na portu 8080.
- `StubServiceBeanFactory` při startu vytvoří **no-op stuby pro 16 backendových služeb**, takže backend nebyl připojen.
- Jediná chyba: `IllegalStateException: Neither BindingResult nor plain target object for bean name '…Model'`
  v JSP žádosti o deaktivaci tarifu (`WEB-INF/jsp/dashboard/tariffDeactivationRequest/…View.jsp`).
  Controller před renderem nevložil form objekt do modelu. Typický problém přepisu z portletů:
  ztracená `@ModelAttribute` metoda.

### 3.7 Předchozí PoC `customercare-web` (detail: [`poznamky/04`](../poznamky/04-poc-modul-customercare-web.md))
- Podle `$FE/customercare-web/MIGRATION_PROGRESS.md` ⚠: „100 % JSP“, 43 controllerů, 522 handlerů,
  vše na mock datech.
- ✔ Z build výstupů: jeden `DispatcherServlet`, 15 stub TLD (portlet/aui/liferay tagy → no-op nebo
  „vykresli tělo“), `portal:*URL` → `GenericUrlTag`, `datasource-stub.xml` se stuby a 4 demo
  implementacemi služeb.
- ✔ (javap) Kompatibilní `MessageTag` nepoužívá `MessageSource`, takže **překlady nefungují**
  a vypisují se klíče nebo výchozí hodnoty. ⚠ Neověřeno za běhu.
- Session a SSO portál nejsou nahrazeny (jen fake login do `HttpSession`). Na classpath zůstává
  `portlet-api` a `spring-webmvc-portlet`.

---

## 4. Co je na migraci těžké (seřazeno podle rizika)

1. **Session a identita ze SSO portálu.** Nejde o Liferay, ale o externí systém sdílející session.
   Bez náhrady aplikace neví, kdo je přihlášen a s jakým zákazníkem pracuje. Řeší se architektonicky
   (duální session, Redis, feature flag), ne přepisem kódu.
2. **Backend ve stejném kontextu, starý stack.** Frontend s sebou táhne celý backend (Spring 4, Hibernate 4,
   `javax.*`). Moderní Spring Boot (3+/4, Jakarta) nelze použít bez upgradu backendu nebo bez
   oddělení FE a BE (REST klient nebo adaptér). Spojení „odstranění Liferay + upgrade Javy“
   (komentář klienta) toto riziko násobí.
3. **Vlastní dispatch** (dashboard podle `page`, objednávky jako page-flow scénář). Je nutné ho
   transformovat na URL routování. Týká se dvou největších oblastí (~970 JSP).
4. **Skryté Liferay závislosti ve vlastních tazích.** JSP vypadají „čistě“, ale `portal:*URL` a další
   tagy sahají na `LiferayPortletURL`. Tagů jsou stovky (~16 000 použití), a když se nahradí no-op stubem,
   funkce potichu zmizí.
5. **Portletová sémantika fází.** Action → render (render parametry, `@SessionAttributes`,
   `@ModelAttribute` per fáze) je nutné převést na PRG a explicitní model. Chyba z logu je přesně tento případ.
6. **i18n.** Překlady jdou z DB a externího CMS přes vlastní tag. V PoC nefungují.
7. **Kompozice stránky.** Portál skládal stránku z více portletů (hlavička, kontext zákazníka,
   informační hlášky, obsah) a theme. Bez portálu je potřeba layout šablona a explicitní vkládání fragmentů.
8. **Autorizace.** Vlastní ACL v tazích a dispatcheru. Je nutné ho zachovat a otestovat (Liferay role se
   nepoužívají, což je výhoda).
9. **Rozsah.** ~227 k LOC Java, 1241 JSP. AI přepis „1:1“ dává rychlý, ale mělký výsledek (viz PoC);
   pokrok je třeba měřit funkčními scénáři.
10. **Citlivé údaje ve zdrojích.** Root POM obsahuje hesla a interní hostname v profilech; zdroje
    a testy obsahují interní URL a testovací klíče ⚠ (zjištěno průzkumem, hodnoty záměrně neuvádím).

Co **není** těžké (na rozdíl od typických Liferay projektů): žádný Service Builder, žádné Liferay
permissions, minimum Liferay UI tagů, hook jen na provisioning portálu.

---

## 5. Kandidáti na rozsah dema

| | A) Blacklist nezákazníků | B) Zrušení služby (deaktivace tarifu) | C) Plánované odstávky |
|---|---|---|---|
| Kde | vlastní portlet `noncustomerblacklist` | stránka v portletu `dashboard` | stránka v portletu `dashboard` |
| Kód | 4 controllery (~970 LOC), 7 modelů, 5 validátorů, 4 JSP (~1 800 LOC) | controller ~700 LOC + manager ~830 LOC + model/validátory ~940 + 9 JSP | controller ~220 LOC + 1 JSP |
| Fáze | render + action + resource, `@SessionAttributes` | render + action + 9× resource | resource + action |
| Liferay | nepřímo (URL tagy, base controller, dispatcher) | `PortalUtil`, dashboard dispatch + friendly URL, session kontext zákazníka | dashboard dispatch |
| Backend | 1 služba | 7+ služeb | integrační klient |
| V prezentaci | ano (PoC search + create) | **ano (hlavní ukázka, before/after)** + chyba z logu | ano (mock) |
| Co naučí | základní mapování portlet → web: fáze, PRG, validace, ACL, URL tagy | dashboard dispatch → URL, kontext zákazníka v session, past `@ModelAttribute`, více služeb za portem | málo nového |

---

## 6. Návrh rozsahu dema a otevřená rozhodnutí

**Doporučení: A + B (zjednodušeně).**
- **A – Blacklist nezákazníků:** vyhledávání → výsledky → detail → vytvoření → deaktivace.
  Učebnicový portlet, který pokrývá všechny tři fáze, validaci, `@SessionAttributes` a ACL privilegium.
- **B – Zrušení služby:** výběr produktu zákazníka → formulář (datum podání, typ zrušení, „ke dni“)
  → odeslání. Ukazuje, jak se rozloží dashboard dispatch (parametr `page` → skutečné URL), jak se
  pracuje s „aktuálním zákazníkem“ ze session a jak se vyhnout chybě z logu. Je to i hlavní ukázka
  z prezentace. Business pravidla zjednodušená a explicitně označená.
- C vynechat: nenaučí nic, co A a B nepokryjí.

**Rozhodnuto (2026-10-08):** rozsah A + B; frontend podle podkladů = Spring MVC + JSP
(server-side), resource/AJAX volání → JSON endpointy; JSF se v projektu nevyskytuje ✔;
Java 21; security se nemění. Podklady nemají návrh security ✔, jen zachování kontraktu session
fasády s identitou uživatele, takže demo použije fasádu + fake login + vlastní ACL jako legacy.

**Původně otevřená rozhodnutí (pro kontext):**
1. **Frontend:** podklady chtějí server-side rendering bez SPA, zadání dema chce REST API. Návrh:
   render a action fáze → server-side stránky (Spring MVC + šablony, PRG), resource fáze → REST/JSON
   endpointy, které stránky volají AJAXem. Tak jsou splněny oba požadavky a mapuje se to 1:1 na portletové fáze.
   Volba šablon: JSP (věrné podkladům, ve Spring Bootu jen jako WAR) vs. Thymeleaf (doporučený pro Boot).
2. **Java:** aktuální LTS je Java 25. Lokálně je nainstalováno JDK 21. Buď doinstalovat JDK 25,
   nebo použít 21 (také LTS).
3. Autentizace v demu: jednoduchý in-memory uživatel s ACL privilegiem (Spring Security)
   jako náhrada SSO portálu, nebo bez přihlášení.
