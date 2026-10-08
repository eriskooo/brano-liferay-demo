# Spec: demo migrácie dvoch portletov z Liferay 6.2 na Spring Boot

Dátum: 2026-10-08 · Stav: návrh na schválenie · Súvisí: [`docs/ANALYZA.md`](../../ANALYZA.md),
[`poznamky/`](../../../poznamky/README.md)

## 1. Cieľ a kontext

Učebné demo, na ktorom je vidieť, **o čo pri migrácii legacy CRM z Liferay ide**. Zrozumiteľnosť
a komentované rozhodnutia majú prednosť pred úplnosťou. Repo pôjde na GitHub, takže všetok kód
a ukážky „pred“ sú napísané nanovo a anonymizované (pravidlá v `poznamky/00-konvencie.md`).

Smer migrácie určujú podklady (prezentácie + Q&A) a demo sa ho drží:
- business funkcie aj technická filozofia **1:1**: server-side stránky, žiadne SPA, stavová session;
- **Spring MVC controllery + JSP**. Vlastné portlet tagy sa reimplementujú **s rovnakým rozhraním**
  bez Liferay, aby JSP zostali takmer nezmenené;
- AJAX volania (portletová resource fáza) zostávajú, len idú na bežné HTTP endpointy;
- session fasáda s identitou používateľa: **zachovať kontrakt, vymeniť implementáciu**;
- security sa nemení: vlastné ACL privilégiá, žiadny Spring Security (podklady ho nenavrhujú);
- backend služby „necháváme“: demo ich nahrádza in-memory implementáciami za rovnakým rozhraním.

Rozhodnutia používateľa: rozsah **A (blacklist ne-zákazníkov) + B (zjednodušené zrušenie služby)**,
**Java 21**, JSP, tagy s rovnakým rozhraním, security nemeniť.

## 2. Technológie a projekt

| Vec | Voľba | Prečo |
|---|---|---|
| Java | 21 (LTS) | rozhodnutie používateľa, nainštalovaná lokálne |
| Framework | Spring Boot **4.1.1** (najnovšia stabilná v Maven Central k 2026-10-08; JSP + testy overené spikom) | cieľ zadania; Jakarta EE 11, Tomcat 11 |
| Build | Maven, jeden modul, `pom.xml` v koreni repa | `mvn verify` z koreňa |
| Balenie | **WAR** (spustiteľný: `java -jar`) + `tomcat-embed-jasper` + Jakarta JSTL | JSP v Spring Boot funguje len s WAR |
| View | JSP v `src/main/webapp/WEB-INF/jsp/` | podklady: zachovať JSP |
| Validácia | Jakarta Bean Validation (`spring-boot-starter-validation`) | náhrada vlastných `Validator` tried, ukážka `javax` → `jakarta` |
| Dáta | in-memory, vymyslené, nasadené pri štarte | backend nie je k dispozícii |
| Testy | JUnit 5, Spring `MockMvc`, `@SpringBootTest(webEnvironment = RANDOM_PORT)` | |

Overené spikom (2026-10-08): Boot 4.1.1 + `spring-boot-starter-webmvc` + `tomcat-embed-jasper` + Jakarta JSTL
vykreslí JSP v teste aj pri `java -jar` WAR; EL číta Java recordy.

Koordináty: `groupId=com.example.crm`, `artifactId=crm-web-demo`, koreňový balík `com.example.crm`.

## 3. Architektúra

```
prehliadač (HTML + malý JS pre AJAX)
   │  GET/POST stránky                  │  AJAX (fragment HTML / JSON)
   ▼                                    ▼
DispatcherServlet ── LegacyPortletInterceptor (nastaví base path „portletu“, kontrola ACL)
   │
   ├── blacklist/*Controller      → JSP blacklist/*.jsp   (+ fragmenty pre AJAX)
   ├── deactivation/*Controller   → JSP dashboard/deactivation/*.jsp
   ├── login/FakeLoginController  → JSP login.jsp
   │
   ├── session/CrmSession (fasáda, kontrakt ako legacy) ── HttpSessionCrmSession
   ├── acl/AclService (privilégiá podľa operátora, in-memory)
   └── backend porty: BlacklistService, CustomerProductService, DeactivationService
                       └── InMemory* implementácie (miesto pre budúci adaptér na reálny backend)
```

Balíky (`com.example.crm.`):

| Balík | Obsah | Nahrádza (legacy) |
|---|---|---|
| `session` | `CrmSession` (interface), `HttpSessionCrmSession`, `OperatorUser`, `CustomerContext`, `UiMessage` | session fasáda nad `PortletSession` + SSO portál |
| `acl` | `AclService`, `Privilege` (enum), `RequiresPrivilege` (anotácia), `AclDeniedException` | vlastné ACL služby a `aclPageFilterTag` |
| `web.portlet` | `LegacyPortlet` (anotácia s base path), `LegacyPortletInterceptor`, `WebConfig` | `DispatcherPortlet` + `<portlet>-portlet.xml` + friendly URL mapper |
| `web.tag` | tag handlery `portal:*`, `common:*` + TLD | vlastné taglib s Liferay závislosťou |
| `web.error` | `@ControllerAdvice` (ACL → 403 stránka, neznámy záznam → 404) | `SimpleMappingExceptionResolverCustom` + hooked chybové JSP |
| `login` | `FakeLoginController` | „Fake login portlet“ |
| `blacklist` | controllery, formulárové modely, služba (port) + in-memory implementácia | portlet blacklistu ne-zákazníkov |
| `deactivation` | controllery, model, služby (porty) + in-memory implementácie | stránka deaktivácie tarifu v dashboard portlete |

### 3.1 Session fasáda (`CrmSession`)
Kontrakt drží tvar legacy fasády v zmenšenom rozsahu:
- identita: `setOperator(OperatorUser)` / `getOperator()` (interný používateľ), `setCustomer(CustomerContext)` /
  `getCustomer()` (vybraný zákazník);
- všeobecné atribúty: `setGlobalAttribute(key, value)`, `getGlobalAttribute(key)`, `removeGlobalAttribute(key)`;
  kľúče majú prefix (`APP_`), ako navrhuje prezentácia pre oddelenie namespace;
- hlášky pre používateľa: `pushInfo(key)`, `pushError(key)`, `popMessages()`. Prežijú redirect
  a layout ich zobrazí raz.

Implementácia `HttpSessionCrmSession` je tenký obal nad `HttpSession` aktuálneho requestu (`HttpSessionCrmSession.of(request)`);
controllery ju dostanú ako parameter metódy cez `HandlerMethodArgumentResolver` (bez scoped proxy – jednoduchšie testovanie). Komentár v kóde
vysvetlí, kde by v produkcii bol Spring Session + Redis a feature flag „zdroj čítania“ z prezentácie.
Ten sa v deme neimplementuje.

### 3.2 Fake login
`GET /login` ponúkne pevný zoznam operátorov (napr. *čitateľ*: `BLACKLIST_VIEW`; *správca*:
`BLACKLIST_VIEW` + `BLACKLIST_MODIFY` + `DEACTIVATION`) a zákazníkov. `POST /login` uloží výber do `CrmSession`.
Bez operátora v session interceptor presmeruje na `/login`. Toto **nie je** autentifikácia, ale
ekvivalent legacy fake login portletu, a je to v README výslovne uvedené.

### 3.3 ACL
- `@RequiresPrivilege(Privilege.X)` na handler metóde alebo triede; interceptor overí cez
  `AclService` a pri chýbajúcom privilégiu vyhodí `AclDeniedException` (→ 403 stránka).
- V JSP rovnaké rozhranie ako legacy:
  - `<common:aclPageFilterTag privilegeType="…" wireframe="…"/>` – zastaví render stránky bez privilégia;
  - `<common:aclFilter privilegeType="…">…</common:aclFilter>` – zobrazí telo len s privilégiom.
  - Atribút `wireframe` sa prijíma kvôli kompatibilite a ignoruje (komentár vysvetlí prečo).

### 3.4 Portlet URL vrstva (jadro „tagy s rovnakým rozhraním“)
Legacy JSP vyrábajú URL takto: `<portal:actionURL var="u"><portal:param name="action" value="searchAction"/></portal:actionURL>`.
Controller sa vyberal parametrom `viewPage`, metóda parametrom `action` alebo `render`.

Nová konvencia (jediné miesto, kde sa portletové URL prekladajú na servletové):
- Každý migrovaný „portlet“ je skupina controllerov s anotáciou `@LegacyPortlet("/blacklist")`.
  Base path môže obsahovať premenné z URL, napr. `@LegacyPortlet("/dashboard/{customerId}")`.
  Interceptor ich doplní z aktuálneho requestu a uloží výsledný base path do request atribútu.
- `portal:actionURL` → `{ctx}{base}/{hodnota param 'action'}` + ostatné parametre ako query string.
  Formulár naň robí POST.
- `portal:renderURL` → `{ctx}{base}/{hodnota param 'render' alebo 'viewPage'}` + ostatné parametre.
- `portal:resourceURL id="x"` → `{ctx}{base}/resource/{x}` + parametre.
- `portal:param name value` → pridá parameter rodičovskému URL tagu.
- Podporované atribúty ako v legacy TLD: `var`, `escapeXml` (a `id` pri resourceURL).
  `name` a `copyCurrentRenderParameters` sa prijmú a zdokumentujú ako ignorované.
- Ak tag nedostane povinný segment (`action`/`render`/`viewPage`/`id`), vyhodí `JspException`
  so zrozumiteľnou správou. Nikdy nič ticho nevynechá (poučenie z PoC, viď `poznamky/07-pasce.md`).

Controllery potom majú normálne mapovanie, napr. `@PostMapping("/searchAction")` v triede
s `@RequestMapping("/blacklist")`. Táto konvencia je zámerne jednoduchá. V `MIGRACIA.md` bude
vysvetlené, prečo reálny projekt potrebuje explicitnú tabuľku routovania („deeplinker“ z prezentácie).

### 3.5 i18n
`<common:message key="…" [includeKey] [var]>` s `<common:msgarg value="…"/>` → Spring `MessageSource`
(`messages.properties`, slovenčina). Chýbajúci kľúč sa zobrazí ako `??key??` a zaloguje sa WARN.
V PoC sa kľúče zobrazovali potichu, tu je problém viditeľný. Preklady z DB alebo CMS sú mimo rozsahu
(komentár: miesto pre vlastný `MessageSource`).

### 3.6 Layout
Spoločný `WEB-INF/jsp/layout/header.jspf` + `footer.jspf` (operátor, vybraný zákazník, hlášky
z `CrmSession`, odhlásenie) a neutrálne statické CSS v `src/main/webapp/css/`. Nahrádza Liferay
theme a skladanie stránky z viacerých portletov. Bez brandingu klienta.

## 4. Feature A – blacklist ne-zákazníkov

Doména (zjednodušená, vymyslené dáta, ~8 záznamov):
- `BlacklistRecord`: `id`, `state` (`ACTIVE`, `INACTIVE`), `stateReason` (napr. podvod, neplatenie,
  zneužitie – enum), `conditionType` (napr. `BLOCK_ALL`, `REQUIRE_DEPOSIT`, `MANUAL_APPROVAL` – enum),
  `validFrom`, `validTo` (null = neobmedzene), osoba (`firstName`, `lastName`, `birthDate`),
  identifikátory (`List<Identifier>`: typ `BIRTH_CODE` / `ID_CARD` / `PASSPORT` / `COMPANY_REG_NO` + hodnota),
  `notes` (`List<Note>`: text, autor = operátor, čas).
- Ktorý `conditionType` je povolený pre ktorý `stateReason`, určuje služba
  (vďaka tomu má AJAX refresh zmysel).

Stránky a endpointy (base `/blacklist`, trieda `@LegacyPortlet("/blacklist")`):

| Legacy | Nové | Privilégium | Pozn. |
|---|---|---|---|
| render `search` | `GET /blacklist/search` | VIEW | formulár kritérií (meno, priezvisko, identifikátor, stav) |
| action `searchAction` | `POST /blacklist/searchAction` | VIEW | validácia (aspoň 1 kritérium) → PRG na `GET /blacklist/searchresult`; kritériá v `@SessionAttributes` ako v legacy; bez výsledku → `pushInfo` + späť na search |
| render `searchresult` | `GET /blacklist/searchresult` | VIEW | tabuľka výsledkov, odkaz na detail |
| render `detail` | `GET /blacklist/detail?detailId=` | VIEW | detail, poznámky, identifikátory; formuláre zmeny len s MODIFY (`aclFilter`) |
| action `changeAction` | `POST /blacklist/changeAction` | MODIFY | zmena stavu/platnosti; deaktivácia = stav `INACTIVE` + `validTo=dnes` → PRG na detail + `pushInfo` |
| action `addNoteAction` | `POST /blacklist/addNoteAction` | MODIFY | pridanie poznámky → PRG na detail |
| resource `removeIdentAction` | `POST /blacklist/resource/removeIdentAction` | MODIFY | AJAX, vráti HTML fragment zoznamu identifikátorov (ako legacy `ModelAndView`) |
| render `create` | `GET /blacklist/create` | MODIFY | formulár nového záznamu |
| resource `refreshCreateConditionTypes` | `GET /blacklist/resource/refreshCreateConditionTypes?stateReason=` | MODIFY | AJAX, vráti HTML fragment `<select>` povolených typov podmienok |
| action `createAction` | `POST /blacklist/createAction` | MODIFY | Bean Validation (povinné polia, `validTo` ≥ `validFrom`, aspoň 1 identifikátor, formát podľa typu) → chyby späť do formulára; úspech → PRG na detail + `pushInfo` |

Legacy „navigačné“ akcie (`gotoCreateAction`, `gotoSearchAction`, `clearFieldsAction`) robili v action
fáze len presmerovanie na iný render. V deme sa z nich stanú obyčajné odkazy `portal:renderURL`
(GET). Je to typické zjednodušenie pri migrácii a v `MIGRACIA.md` bude vysvetlené.

Vynechané z legacy (zdokumentované v `MIGRACIA.md`): pridanie identifikátora a kontaktu cez AJAX
v detaile, adresa, firemné údaje, kontakty, mazanie osobných údajov.

## 5. Feature B – zrušenie služby (zjednodušené)

Legacy: stránka `tariffDeactivation` v dashboard portlete, vybraná parametrom `page` cez vlastný
`HandlerMapping`. Model je v triede (`@ModelAttribute` + `@SessionAttributes`). Friendly URL
v tvare `/{zákazník}/…/{asset}/{page}/{action}`. Chyba z PoC logu vznikla práve tu.

Doména: `CustomerProduct` (`id`, `name`, `type` TARIF/PRODUKT, `description`, `customerId`),
`DeactivationType` (`TRANSFER_TO_CUSTOMER` = prevod na iného zákazníka, `CUSTOMER_DEACTIVATION` =
zrušenie, `CUSTOMER_POSTPRE` = prevod na predplatenú kartu), `DeactivationRequest` (produkt, typ,
`noticeDate` = dátum podania, `effectiveDate` = ku dňu, operátor, čas).

Pravidlá (vymyslené, v kóde označené ako demo):
- `noticeDate` predvyplnený na dnes, nesmie byť v budúcnosti;
- najskorší `effectiveDate` podľa typu: zrušenie = `noticeDate` + 30 dní, prevod na predplatenú
  kartu = `noticeDate` + 1 deň, prevod na iného zákazníka = `noticeDate` (hneď);
- produkt musí patriť zákazníkovi v session (inak 404). Ide o náhradu legacy kontroly kontextu zákazníka.

| Legacy | Nové | Privilégium |
|---|---|---|
| dashboard prehľad produktov | `GET /dashboard/{customerId}/products` | DEACTIVATION |
| `page=tariffDeactivation` render | `GET /dashboard/{customerId}/products/{productId}/deactivation` | DEACTIVATION |
| resource (výpočet dátumu) | `GET …/deactivation/resource/earliestDate?type=&noticeDate=` → **JSON** `{"earliestDate":"2026-11-07"}` | DEACTIVATION |
| action `submit` | `POST …/deactivation/submit` → PRG na `…/deactivation/done` | DEACTIVATION |

`{customerId}` v URL musí sedieť so zákazníkom v session, inak presmerovanie na jeho dashboard – rieši to samostatný
`CustomerContextInterceptor` (náhrada legacy interceptora, ktorý vyžadoval kontext zákazníka).

Formulár deaktivácie **nepoužíva** `@SessionAttributes`: legacy ho držal v session, čo pri otvorení formulára pre iný
produkt prinesie zastaraný stav. Demo to v `MIGRACIA.md` vysvetlí ako vedomú zmenu.

Pasca z logu ukázaná explicitne: controller má `@ModelAttribute("deactivationForm")` metódu
s komentárom, prečo je nevyhnutná, a test `GET …/deactivation` overí, že model obsahuje form
objekt a stránka sa vykreslí (HTTP 200, obsahuje formulár).

## 6. Chybové stavy

| Situácia | Správanie |
|---|---|
| bez operátora v session | redirect `/login` |
| chýbajúce ACL privilégium | 403 stránka s menom privilégia |
| neexistujúci záznam / produkt cudzieho zákazníka | 404 stránka |
| validačná chyba formulára | ten istý formulár s chybami pri poliach (bez redirectu) |
| AJAX bez privilégia | HTTP 403 bez layoutu |
| AJAX bez prihlásenia (vypršaná session) | HTTP 401 bez layoutu (nie redirect na login, ktorý by JS vložil do stránky) |
| chýbajúci i18n kľúč | `??key??` + WARN log |
| zlé použitie URL tagu v JSP | `JspException` s popisom |

## 7. Testovanie

TDD po malých krokoch. Testy, ktoré musia existovať a prejsť v `mvn verify`:
- **Unit:** URL tagy (skladanie URL, `var`, `escapeXml`, chyba bez segmentu), message tag,
  ACL tagy, `HttpSessionCrmSession` (hlášky prežijú redirect a zobrazia sa raz), pravidlá dátumov
  deaktivácie, in-memory služby (vyhľadávanie, zmena stavu, povolené typy podmienok).
- **Web (`@WebMvcTest` / MockMvc):** routy, PRG redirecty, `@SessionAttributes` kritériá,
  validačné chyby, ACL 403 (čitateľ nesmie `createAction`), redirect bez loginu, nesúhlasiaci
  `customerId`, JSON endpoint, AJAX fragmenty.
- **Integračné (`@SpringBootTest` na náhodnom porte, reálny Tomcat + Jasper):** prihlásenie →
  vyhľadávanie → detail → deaktivácia blacklist záznamu; prihlásenie → produkty → formulár zrušenia
  → odoslanie → potvrdenie. Overuje **skutočne vykreslené HTML** (JSP sa kompilujú a tagy fungujú).

Overenie hotového dema (spustením, nie odhadom): `mvn verify` zelené, potom `java -jar target/*.war`
a prechod hlavných stránok cez `curl` so session cookie.

## 8. Dokumentácia (výstupy)

- `README.md` – čo demo ukazuje, predpoklady (JDK 21, Maven), spustenie, demo používatelia,
  štruktúra, čo je mimo rozsahu.
- `docs/MIGRACIA.md` – pre každý kus (portlet → controller, fázy → PRG/AJAX, URL tagy, i18n tag,
  ACL, session fasáda, dashboard dispatch → URL, `@ModelAttribute` pasca, validácia, theme → layout):
  anonymizovaná minimálna ukážka **pred**, ukážka **po** s odkazom na súbor, **prečo**. Na konci
  mapovacia tabuľka Liferay koncept → Spring Boot náhrada (rozšírená verzia `poznamky/05`).
- `poznamky/` – doplniť poznatky z implementácie (najmä pasce a čo bolo pri Spring Boot + JSP neočakávané).
- Kód obsahuje komentáre s „prečo“ pri každom migračnom rozhodnutí, nie opis toho, čo kód robí.

## 9. Mimo rozsahu

Reálny backend a jeho adaptéry, SSO portál, duálna session / Redis / feature flag, autentifikácia
(Spring Security), preklady z DB/CMS, pôvodný theme a branding, objednávkové scenáre (page-flow),
ostatné portlety a dashboard stránky, deeplinky z externých systémov, JBoss nasadenie, upgrade backendu.

## 10. Git

`.gitignore` je pripravený (`/input/`, `target/`, IDE, `poznamky/LOCAL-*.md`). Repo je inicializované.
**Nič sa necommituje ani nepushuje.** To robí používateľ. Pred odovzdaním prebehne anonymizačná
kontrola (grep na identifikátory klienta) všetkých súborov mimo `input/`.
