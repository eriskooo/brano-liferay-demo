# Analýza: legacy CRM na Liferay 6.2 → Spring Boot

> Anonymizovaná analýza podkladov v `input/` (git-ignorované). Skratky (`$FE`, `<pkg>`, „SSO portál“…)
> sú vysvetlené v [`poznamky/00-konvencie.md`](../poznamky/00-konvencie.md).
> Značky: ✔ = overené vlastným príkazom (grep/find/unzip/javap) · ⚠ neoverené = tvrdenie podkladov
> alebo odvodenie, ktoré sa nedalo overiť (chýbajúce zdroje, nedá sa spustiť).

Cesty: `CP` = `$FE/customercare-portlets/src/main`, `J` = `CP/java/<pkg>/crm/frontend`,
`FBC` = `$FE/frontend-base-customization/src/main/java/<pkg>/crm/frontend/base`,
`CARGO` = `$FE/customercare-web/target/cargo/configurations/tomcat9x/webapps/customercare-web`.

---

## 1. O aký projekt ide

**Legacy CRM pre zákaznícku podporu** telco operátora. Interní operátori ho používajú na prácu so
zákazníkom: dashboard zákazníka, objednávky a zmeny služieb, vyúčtovanie, požiadavky zákazníka,
scoring a blacklist.

- Frontend je **portletová aplikácia na Liferay 6.2** (root POM: `liferay.version=6.2.5`) bežiaca na JBoss.
  Stack: Java 8, Spring 4.3.6, Hibernate 4.2, Oracle 19 (`input/pom.xml`; architektúra zo slajdu 14 prezentácie).
- Backend a integrácie (REST/SOAP voči integračnej vrstve, message broker, SSO portál) sú **v tom istom JVM
  a Spring kontexte**: `CP/resources/context-jboss.xml` importuje backend kontexty (DAO, služby, transakcie,
  WS/REST klientov) z jarov.
- Zdrojáky backendu v podkladoch **nie sú**. Je len frontend modul `$FE` a root POM, v ktorom sú
  sesterské moduly (`db-layer`, `backend`, `integration`, …) uvedené, ale chýbajú. Legacy projekt
  **nejde zbuildovať** (súkromný Maven repozitár, chýbajúce moduly).

### Čo je zadanie podľa podkladov
Zdroj: prezentácie `input/*-AI*.pptx` a Q&A dokument `input/*-odpovedi-komentare.*` (detail v
[`poznamky/01`](../poznamky/01-zadanie-z-podkladov.md)).

- **Odstrániť Liferay** ako nepodporovanú a rizikovú závislosť, s **business funkcionalitou 1:1**
  a s **technickou filozofiou 1:1**. Znamená to: žiadne SPA, žiadny presun logiky medzi FE a BE,
  stavová aplikácia (slide 2).
- Cieľový stack v podkladoch: **Spring MVC + tie isté JSP na plain Tomcat**. Portlet tagy sa
  reimplementujú bez Liferay, theme sa nahradí statickými súbormi a backend služby zostávajú (slajdy 4, 5, 13).
- Otvorené témy podľa podkladov: duálna session s Redisom (slide 12), deeplinky a odkazy „späť“
  („treba rozpracovať“), CMS preklady a upgrade Javy spolu s odstránením Liferay (komentáre klienta).
  Čísla „375+ controllerov“ a „30+ objektov v session“ zo slajdu 12 sú ⚠ neoverené.

> **Rozpor so zadaním dema:** zadanie dema žiada *Spring Boot + REST API*, podklady žiadajú
> *server-side JSP bez SPA*. Návrh riešenia je v kap. 6.

---

## 2. Moduly (`$FE/pom.xml`)

| Modul | Čo to je | Zdroj v podkladoch |
|---|---|---|
| `customercare-portlets` | legacy portlet WAR (všetky obrazovky) | ✔ plný `src/` (~1418 Java súborov, **1241 JSP** ✔, 219 tag files) |
| `frontend-base-customization` | zdieľaná knižnica: session fasáda, vlastné tagy, portal utils, i18n, cache | ✔ `src/` |
| `customercare-theme` | Liferay theme (Velocity šablóny, LAR export obsahu) | len prebuilt WAR |
| `customercare-web` | **predchádzajúci PoC** „bez Liferay“ (Spring MVC, Tomcat 9 cez Cargo) | ⚠ **len `target/`**, žiadny `src/` |
| `customercare-testing` | záťažové testy (Liferay portlet) | `src/`, nie je v `<modules>` |
| `one-portal-portlets` | portlet session managera SSO portálu (Spring 3, rok 2016) | len prebuilt WAR |

**Stav archívu:** v `$FE/customercare-web/target/customercare-web/WEB-INF/lib` je len 21 jarov a chýba
backend JAR. Táto kópia nemá ani `web.xml`, ani JSP. V nasadenej kópii `CARGO/WEB-INF/lib` je však
**186 jarov a ten istý JAR je prítomný a neporušený** ✔ (`unzip -t`: bez chýb). Neúplná je teda len
jedna z troch kópií build výstupu, nie celý archív. Kópie sa líšia aj verziou: `CARGO/WEB-INF/classes`
sú staršie ako `customercare-web/target/classes` ✔ (napr. `TariffDeactivationWebController` je len v novšej).

---

## 3. Architektúra legacy frontendu

### 3.1 Portlety ✔ `CP/webapp/WEB-INF/portlet.xml`
13 portletov (JSR-286), všetky `instanceable=false`, `private-session-attributes=false`
(`liferay-portlet.xml`):

| Oblasť | Portlety | Dispatch | JSP |
|---|---|---|---|
| Shell | `customercontext`, `header`, `login` (fake) | anotácie | 4 + 13 + 1 |
| Dashboard zákazníka | `dashboard` | **vlastný `DashboardHandlerMapping` podľa parametra `page`** | 529 |
| Objednávky | `order` | **vlastný `CustomScenarioHandlerMapping` (page-flow scenár/krok)** | 444 |
| Vyúčtovanie | `billingmanagement` | anotácie | 62 |
| Požiadavky zákazníka | `customerrequest`, `scoringrequest` | `CustomerRequestTypeHandlerMapping` podľa `@CrType` / anotácie | 28 + 1 |
| Vyhľadávanie/admin | `noncustomerblacklist`, `documentsearch`, `contactpersonsearch` | anotácie | 5 + 4 + 3 |
| Vyradené | `entityaccessmanagement`, `documents` | `DecommissioningDispatcherPortlet` | 24 + 11 |

(počty JSP podľa adresárov `CP/webapp/WEB-INF/jsp/<oblasť>/`; `common/` ďalších 112)

### 3.2 Typ portletov: Spring Portlet MVC ✔
- Portlet triedy sú vlastné podtriedy Spring `DispatcherPortlet` (`J/portlet/DispatcherPortletCustom.java`,
  `DispatcherPortletCustomerContext.java`). Každý portlet má vlastný Spring kontext
  `CP/webapp/WEB-INF/<portlet>-portlet.xml` s view resolverom `/WEB-INF/jsp/<portlet>/`.
- Anotácie ✔ (grep `CP/java`): `@Controller` 246, `@RenderMapping` 174, `@ActionMapping` 347,
  `@ResourceMapping` 598. Ďalej `@SessionAttributes` 101 a `@ModelAttribute` 745.
- Žiadne `MVCPortlet`, `GenericPortlet` ani JSF.
- Spoločný base controller `J/portlet/controller/BaseCustomerCareController.java` (~1900 LOC) dedí
  z triedy externej core knižnice ⚠ (zdroj nie je k dispozícii).

### 3.3 Použité Liferay API

| Oblasť | Nález | Kde |
|---|---|---|
| **Service Builder** | **nepoužíva sa** ✔ (žiadny `service.xml`) | – |
| Priame importy `com.liferay.*` | 119 Java súborov ✔ v `CP/java`, 17 v FBC | najmä `PortalUtil` (175 volaní, 92× `getHttpServletRequest`) |
| `ThemeDisplay` | 6 Java súborov, v JSP takmer len `getPathThemeImages` | `J/util/ControllerManagerCustomImpl.java`, `J/portlet/DoubleclickProtectedHandlerAdapter.java` |
| `LanguageUtil` / i18n | okrajovo (jazyk/krajina); preklady idú cez vlastný `<common:message>` a `MessageSource` z DB | `FBC/service/i18/MessageSourceServiceCustomImpl.java`, `CP/resources/portlet-base-context.xml` |
| **Permissions** | Liferay `PermissionChecker` len v startup hooku; **business autorizácia = vlastné ACL** | `<common:aclPageFilterTag privilegeType=…>`, ACL služba v `DispatcherPortletCustom` |
| **Hooks** ✔ | `liferay-hook.xml`: `portal.properties` (startup event, phishing-protected session atribút) + `custom-jsp-dir` (meta tagy, chybové stránky 403/404/500) | `CP/webapp/WEB-INF/hooked/` |
| Startup hook | vytvára site, stránky, rozmiestňuje portlety, Expando, Journal články (`*LocalServiceUtil` 60×) | `CP/java/<pkg>/crm/hook/ConfigurationHook.java` + `hook/activity/` |
| **Friendly URL** ✔ | 5 route súborov, mapper pre `order`, `customerrequest`, `scoringrequest` (default) a `dashboard`, `billingmanagement` (vlastný) | `CP/resources/friendly/*.xml`, `J/portlet/CustomerContextFriendlyURLMapper.java` |
| Portlet URL | v Jave sa `createRenderURL` priamo nevolá; URL vyrábajú **vlastné tagy** `portal:actionURL/resourceURL/renderURL`, ktoré pretypujú na `LiferayPortletURL` | `FBC/jsp/tag/portal/AbstractPortletUrlTag.java` |
| Liferay taglibs v JSP | `liferay-ui`, `aui`: 0 použití; `liferay-theme`: 29 súborov; dominujú vlastné `portal:` (~7 200 použití v ~940 JSP) a `common:` (~9 500 v ~1 100 JSP) | `CP/webapp/WEB-INF/tld/` (15 TLD) |
| **Theme** | separátny WAR (Velocity), portlet WAR ho vyžaduje (`liferay-plugin-package.properties`) | `$FE/customercare-theme` |
| CMS | okrajovo: `AssetEntry` pre popisy produktov, Journal článok pre prázdny košík | `BaseCustomerCareController.java` (~ř. 961), `JournalArticleCustomTag.java` |
| Session | `PortletSession` 51× (hlavne PORTLET_SCOPE); hlavná logika vo fasáde | `FBC/service/SessionManagerCustom.java` (~780 LOC), `impl/SessionManagerCustomImpl.java` (~1750 LOC) |
| Portlet filter | obmedzovač paralelných renderov z knižnice SSO portálu | `portlet.xml`, `CP/resources/threadcounter.properties` |
| JBoss | `jboss-deployment-structure.xml` závisí na module Liferay a moduloch SSO portálu | `CP/webapp/WEB-INF/` |

⚠ Čísla bez ✔ pochádzajú z prieskumu kódu (grep cez subagenta) a neboli prepočítané ručne.

### 3.4 Session a identita
- Prihlásený používateľ a časť kontextu žijú v **SSO portáli**. Fasáda session managera ho volá cez
  service locator (`SessionManagerCustomImpl`, metódy `get1PSession`/`set1P*`), API je `provided`
  závislosť. Pre lokálny beh existuje fake (`FBC/portal/fakeservice/*Fake`).
- `getSessionManager()` sa volá 414× v 114 súboroch. Je to **jediný bod**, cez ktorý controllery
  pracujú so session. Na tom stavia návrh „duálnej session“ v prezentácii.

### 3.5 Závislosti na backend službách
- Frontend volá backend **priamo cez Java interfacy** z jarov core knižnice a projektovej customizácie
  (`backend.service.*`, `integration.client.*`). Všetko beží v jednom Spring kontexte; nie je tam
  remoting ani EJB (`CP/resources/context-jboss.xml`).
- Najčastejšie importované služby ⚠: property accessor, požiadavky zákazníka, číselníky, vyhľadávanie
  adries, splátky, dokumenty, ceny, záväzky, scoring, prenos čísla, platobná brána.

### 3.6 Čo hovoria logy (`input/*_war*.log`) ✔
- Logy pochádzajú z **PoC `customercare-web`**, nie z legacy portálu. Sú to dva štarty Tomcatu na porte 8080.
- `StubServiceBeanFactory` pri štarte vytvorí **no-op stuby pre 16 backend služieb**, takže backend nebol pripojený.
- Jediná chyba: `IllegalStateException: Neither BindingResult nor plain target object for bean name '…Model'`
  v JSP žiadosti o deaktiváciu tarifu (`WEB-INF/jsp/dashboard/tariffDeactivationRequest/…View.jsp`).
  Controller pred renderom nevložil form objekt do modelu. Typický problém prepisu z portletov:
  stratená `@ModelAttribute` metóda.

### 3.7 Predchádzajúci PoC `customercare-web` (detail: [`poznamky/04`](../poznamky/04-poc-modul-customercare-web.md))
- Podľa `$FE/customercare-web/MIGRATION_PROGRESS.md` ⚠: „100 % JSP“, 43 controllerov, 522 handlerov,
  všetko na mock dátach.
- ✔ Z build výstupov: jeden `DispatcherServlet`, 15 stub TLD (portlet/aui/liferay tagy → no-op alebo
  „vykresli telo“), `portal:*URL` → `GenericUrlTag`, `datasource-stub.xml` so stubmi a 4 demo
  implementáciami služieb.
- ✔ (javap) Kompatibilný `MessageTag` nepoužíva `MessageSource`, takže **preklady nefungujú**
  a vypisujú sa kľúče alebo defaulty. ⚠ Neoverené za behu.
- Session a SSO portál nie sú nahradené (len fake login do `HttpSession`). Na classpath zostáva
  `portlet-api` a `spring-webmvc-portlet`.

---

## 4. Čo je na migrácii ťažké (zoradené podľa rizika)

1. **Session a identita zo SSO portálu.** Nie je to Liferay, ale externý systém zdieľajúci session.
   Bez náhrady aplikácia nevie, kto je prihlásený a s akým zákazníkom pracuje. Rieši sa architektonicky
   (duálna session, Redis, feature flag), nie prepisom kódu.
2. **Backend v tom istom kontexte, starý stack.** Frontend ťahá celý backend (Spring 4, Hibernate 4,
   `javax.*`). Moderný Spring Boot (3+/4, Jakarta) nejde použiť bez upgradu backendu alebo bez
   oddelenia FE a BE (REST klient alebo adaptér). Spojenie „odstránenie Liferay + upgrade Javy“
   (komentár klienta) toto riziko násobí.
3. **Vlastný dispatch** (dashboard podľa `page`, objednávky ako page-flow scenár). Treba ho
   pretransformovať na URL routovanie. Týka sa dvoch najväčších oblastí (~970 JSP).
4. **Skryté Liferay závislosti vo vlastných tagoch.** JSP vyzerajú „čisto“, ale `portal:*URL` a ďalšie
   tagy siahajú na `LiferayPortletURL`. Tagov je stovky (~16 000 použití), a keď sa nahradia no-op stubom,
   funkcia ticho zmizne.
5. **Portletová sémantika fáz.** Action → render (render parametre, `@SessionAttributes`,
   `@ModelAttribute` per fáza) treba previesť na PRG a explicitný model. Chyba z logu je presne tento prípad.
6. **i18n.** Preklady idú z DB a externého CMS cez vlastný tag. V PoC nefungujú.
7. **Kompozícia stránky.** Portál skladal stránku z viacerých portletov (hlavička, kontext zákazníka,
   info hlášky, obsah) a theme. Bez portálu treba layout šablónu a explicitné vkladanie fragmentov.
8. **Autorizácia.** Vlastné ACL v tagoch a dispatcheri. Treba ho zachovať a otestovať (Liferay roly sa
   nepoužívajú, čo je výhoda).
9. **Rozsah.** ~227 k LOC Java, 1241 JSP. AI prepis „1:1“ dáva rýchly ale plytký výsledok (viď PoC);
   pokrok treba merať funkčnými scenármi.
10. **Citlivé údaje v zdrojoch.** Root POM obsahuje heslá a interné hostiteľské mená v profiloch; zdroje
    a testy obsahujú interné URL a testovacie kľúče ⚠ (zistené prieskumom, hodnoty zámerne neuvádzam).

Čo **nie je** ťažké (na rozdiel od typických Liferay projektov): žiadny Service Builder, žiadne Liferay
permissions, minimum Liferay UI tagov, hook iba na provisioning portálu.

---

## 5. Kandidáti na rozsah dema

| | A) Blacklist ne-zákazníkov | B) Zrušenie služby (deaktivácia tarifu) | C) Plánované odstávky |
|---|---|---|---|
| Kde | vlastný portlet `noncustomerblacklist` | stránka v `dashboard` portlete | stránka v `dashboard` portlete |
| Kód | 4 controllery (~970 LOC), 7 modelov, 5 validátorov, 4 JSP (~1 800 LOC) | controller ~700 LOC + manager ~830 LOC + model/validátory ~940 + 9 JSP | controller ~220 LOC + 1 JSP |
| Fázy | render + action + resource, `@SessionAttributes` | render + action + 9× resource | resource + action |
| Liferay | nepriamo (URL tagy, base controller, dispatcher) | `PortalUtil`, dashboard dispatch + friendly URL, session kontext zákazníka | dashboard dispatch |
| Backend | 1 služba | 7+ služieb | integračný klient |
| V prezentácii | áno (PoC search + create) | **áno (hlavná ukážka, before/after)** + chyba z logu | áno (mock) |
| Čo naučí | základné mapovanie portlet → web: fázy, PRG, validácia, ACL, URL tagy | dashboard dispatch → URL, kontext zákazníka v session, `@ModelAttribute` pasca, viac služieb za portom | málo nového |

---

## 6. Návrh rozsahu dema a otvorené rozhodnutia

**Odporúčanie: A + B (zjednodušené).**
- **A – Blacklist ne-zákazníkov:** vyhľadávanie → výsledky → detail → vytvorenie → deaktivácia.
  Učebnicový portlet, ktorý pokrýva všetky tri fázy, validáciu, `@SessionAttributes` a ACL privilégium.
- **B – Zrušenie služby:** výber produktu zákazníka → formulár (dátum podania, typ zrušenia, „ku dňu“)
  → odoslanie. Ukazuje, ako sa rozbije dashboard dispatch (`page` parameter → skutočné URL), ako sa
  pracuje s „aktuálnym zákazníkom“ zo session a ako sa vyhnúť chybe z logu. Je to aj hlavná ukážka
  z prezentácie. Business pravidlá zjednodušené a explicitne označené.
- C vynechať: neučí nič, čo A a B nepokryjú.

**Rozhodnuté (2026-10-08):** rozsah A + B; frontend podľa podkladov = Spring MVC + JSP
(server-side), resource/AJAX volania → JSON endpointy; JSF sa v projekte nevyskytuje ✔;
Java 21; security sa nemení. Podklady nemajú návrh security ✔, len zachovanie kontraktu session
fasády s identitou používateľa, takže demo použije fasádu + fake login + vlastné ACL ako legacy.

**Pôvodne otvorené rozhodnutia (pre kontext):**
1. **Frontend:** podklady chcú server-side rendering bez SPA, zadanie dema chce REST API. Návrh:
   render a action fázy → server-side stránky (Spring MVC + šablóny, PRG), resource fáza → REST/JSON
   endpointy, ktoré stránky volajú AJAXom. Tak sú splnené obe požiadavky a mapuje sa to 1:1 na portletové fázy.
   Voľba šablón: JSP (verné podkladom, v Spring Boot len ako WAR) vs. Thymeleaf (odporúčané pre Boot).
2. **Java:** aktuálna LTS je Java 25. Lokálne je nainštalovaná JDK 21. Buď doinštalovať JDK 25,
   alebo použiť 21 (tiež LTS).
3. Autentifikácia v deme: jednoduchý in-memory používateľ s ACL privilégiom (Spring Security)
   ako náhrada SSO portálu, alebo bez prihlásenia.
