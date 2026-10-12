# Spec: legacy appka „pred“ na Liferay 6.2 (podprojekt 1)

Dátum: 2026-10-12 · Stav: návrh na schválenie · Súvisí: [`docs/ANALYZA.md`](../../ANALYZA.md),
[`poznamky/`](../../../poznamky/README.md), spec „po“ [`2026-10-08-liferay-migracia-demo-design.md`](2026-10-08-liferay-migracia-demo-design.md)

## 1. Cieľ a kontext

Demo migrácie má ukázať **oba stavy toho istého príkladu**:

1. **Podprojekt 1 (tento spec) – „pred“:** príklady A (blacklist ne-zákazníkov) a B (zrušenie služby)
   bežia ako portlety v **reálnom Liferay 6.2** s rovnakým stackom a mechanizmami ako legacy CRM
   (Java 8, Spring Portlet MVC 4.3, JSP, vlastné tagy, session fasáda, SSO portál).
2. **Podprojekt 2 – „po“:** tie isté príklady na Spring Boot 4.1.1 / Java 21. Vychádza z existujúceho
   specu a plánu z 2026-10-08, ktoré sa v podprojekte 2 upravia (zdieľaná session z Redisu,
   súbeh starej a novej appky za jednou adresou, Boot appka sa presunie do `crm-web/`).

„1:1“ znamená **tie isté mechanizmy, nanovo napísané a anonymizované** – nič sa nekopíruje z `input/`.
Zrozumiteľnosť má prednosť pred úplnosťou; komentáre v kóde vysvetľujú, čo je legacy vzor a prečo
je pre migráciu dôležitý.

Rozhodnutia používateľa (2026-10-12):
- stará appka 1:1 s legacy: Liferay 6.2 + **Java 8** (nie novšia), Spring Portlet MVC;
- reprodukovať: **jadro** (DispatcherPortlet podtrieda, vlastné taglibs, ACL, session fasáda, base controller,
  `@SessionAttributes`), **SSO portál + duálna session** (Redis, feature flag), **startup hook + hooked JSP + theme**;
- **bez** vlastného dashboard dispatchu a friendly URL – príklad B používa štandardné `params = "page=…"`;
- mock SSO portálu = **samostatná HTTP služba + API JAR** v `tomcat/lib/ext`;
- štruktúra = **multi-module podľa legacy** (prístup 1).

## 2. Overené spikom (2026-10-12, vyhodený kód)

- Liferay 6.2 CE GA6 (Tomcat 7.0.62) beží v Dockeri na Jave 8; portlet Spring Portlet MVC 4.3.6
  zbuildovaný len z Maven Central (JDK 21, `--release 8`) sa hot-deployne a render/action/resource fungujú.
- Obojsmerné zdieľanie stavu cez Redis medzi portletom (Java 8) a Spring Boot 4.1.1 (Java 21)
  za nginx na jednej adrese funguje; cookie nastavená v action fáze portletu sa dostane do prehliadača.
- **Java 8 musí mať update ≤ 255.** S 8u504 Liferay nenaštartuje: zabalená ICU4J hodí
  `IllegalArgumentException: Invalid version number … greater than 255` → nevznikne company →
  `ArrayIndexOutOfBoundsException` v `PortalInstances`. Funguje `azul/zulu-openjdk:8u252`.
- Hosť nevidí portlet, ktorý nie je umiestnený na stránke („You do not have the roles required“) →
  portlety sa umiestnia na stránky v startup hooku a hosť dostane VIEW.
- Liferay 6.2.5 API (`portal-service`, `util-java`, `util-taglib`, `util-bridges`) je v Maven Central.
- Štart Liferay ~55–80 s; prvý request počas „zahrievania“ môže vrátiť 400.

## 3. Technológie

| Vec | Voľba | Prečo |
|---|---|---|
| Portál | Liferay Portal **6.2 CE GA6** (6.2.5), Tomcat 7.0.62, HSQL | ako legacy (legacy beží na JBoss; Tomcat bundle je jednoduchší a pre demo postačuje) |
| Java (runtime) | **Zulu 8u252** v Docker image | 1:1 s legacy; update ≤ 255 kvôli ICU (kap. 2) |
| Java (build) | hostiteľský JDK 21, `maven.compiler.release=8` | overené spikom; `--release` garantuje len Java 8 API |
| Framework | Spring 4.3.6.RELEASE (`spring-webmvc-portlet`) | verzia z legacy root POM |
| Portlet / servlet API | `portlet-api` 2.0, `javax.servlet-api` 3.0.1, `jsp-api` 2.2 (`provided`), JSTL 1.2 | Liferay 6.2 = Portlet 2.0 na Tomcat 7 |
| Liferay API | `com.liferay.portal:portal-service` 6.2.5 (`provided`) | `LiferayPortletURL`, hook, `*LocalServiceUtil` |
| JSON / Redis | Jackson 2.9.x, Jedis 2.9.x | dobové, bežia na Jave 8 |
| Testy (legacy) | JUnit 5.10.x + Mockito 4.11 + `spring-test` 4.3.6 (portlet mocky) | globálne pravidlo JUnit 5; obe bežia na Jave 8 |
| SSO mock | Spring Boot **4.1.1**, Java 21, JUnit 5 + MockMvc | je to „externý systém“, nie legacy; rovnaký stack ako „po“ |
| Infra | Docker Compose: Liferay, `redis:7-alpine`, sso-mock, `nginx:1.27-alpine` | jedna adresa `http://localhost:8000` |

## 4. Štruktúra repa

```
legacy/                               Maven multi-module (parent pom), Java 8
├── sso-portal-api/                   JAR (shaded) → tomcat/lib/ext
├── frontend-base-customization/      JAR → WEB-INF/lib portlet WAR-u
├── customercare-portlets/            WAR → hot deploy
└── customercare-theme/               WAR → hot deploy
sso-mock/                             samostatný Maven projekt, Spring Boot 4.1.1, Java 21
docker/
├── docker-compose.yml
├── liferay/Dockerfile, portal-ext.properties
├── nginx/default.conf
├── build-and-deploy.ps1 / .sh        build + kopírovanie WAR-ov do docker/deploy/
└── smoke.ps1 / .sh                   end-to-end overenie cez curl
```

Existujúci plán podprojektu 2 ráta s Boot appkou v koreni repa; presun do `crm-web/` rieši podprojekt 2.
`.gitignore` sa doplní o `docker/deploy/`.

Koordináty: `groupId=com.example.crm.legacy`; balíky `com.example.crm.frontend` (portlety),
`com.example.crm.frontend.base` (knižnica), `com.example.sso.api`, `com.example.ssomock`.

## 5. Moduly

| Modul | Obsah | Ekvivalent v legacy |
|---|---|---|
| `sso-portal-api` | `PortalSessionManager` (interface, `getPortalSession(HttpServletRequest)` vráti `null`, ak session nie je – legacy štýl), `PortalSession`/`PortalOperator`/`PortalCustomer` (DTO), `PortalServiceLocator` (vyberie implementáciu podľa system property), `HttpPortalSessionManager` (`HttpURLConnection` + Jackson, timeouty 1 s / 2 s); Jackson relokovaný cez maven-shade | `provided` API SSO portálu + JBoss modul |
| `frontend-base-customization` | `SessionManagerCustom` + `SessionManagerCustomImpl`, `RequestAwareManager`, `SessionStore` + 3 implementácie, `SharedKeys`, `DispatcherPortletCustom`, `BaseCrmController`, `SimpleMappingExceptionResolverCustom`, `AclService` + `Privilege`, `MessageSourceCustom`, tag handlery `portal:*` a `common:*` + TLD (zabalené v JAR-e, `META-INF/*.tld`) | `FBC/…` |
| `customercare-portlets` | portlety `blacklist`, `dashboard` a `header` (kontext operátora/zákazníka + hlášky), controllery, formuláre, validátor, backend porty + in-memory implementácie, JSP, `portlet.xml`, `liferay-portlet.xml`, `liferay-display.xml`, `liferay-plugin-package.properties`, `blacklist-portlet.xml`, `dashboard-portlet.xml`, `applicationContext.xml` + `backend-context.xml`, `liferay-hook.xml` (startup action + `custom-jsp-dir`), `messages.properties` | `CP/…` |
| `customercare-theme` | Velocity šablóny (`portal_normal.vm`, navigácia), neutrálne CSS; hlavičku nevykresľuje theme sám (k Spring beanom portlet WAR-u sa nedostane), ale vloží portlet `header` cez `$theme.runtime(…)` | `customercare-theme` |

## 6. Tok requestu

```
prehliadač ─► nginx :8000 ─► Liferay /web/crm/blacklist
  → theme (portal_normal.vm) + layout → portlet blacklist (DispatcherPortletCustom)
      → identita: SessionManagerCustom.getOperator()
          → RequestAwareManager (RequestContextHolder → PortletRequest → HttpServletRequest cez PortalUtil)
          → PortalServiceLocator → HTTP sso-mock /sso/api/sessions/{SSO_ID}  (cache v atribúte requestu)
      → ACL: privilégium portletu (init-param) → AclService
      → BlacklistSearchController @RenderMapping → JSP (portal:actionURL, common:message, common:aclFilter)
```

- **Action:** `@ActionMapping(params = "action=…")` → fasáda / `@SessionAttributes` →
  `response.setRenderParameter("render", …)` (prípadne `viewPage`).
- **Resource:** `@ResourceMapping("…")` → HTML fragment (`ModelAndView`) alebo JSON.
- **Bez SSO session** (chýba cookie `SSO_ID` alebo portál vráti 404): v action fáze
  `ActionResponse.sendRedirect` na `/sso/login?returnUrl=…`; v render fáze portlet nevie presmerovať,
  vykreslí výzvu s odkazom „Prihlásiť“ (legacy obmedzenie, zdokumentovať).
- **SSO nedostupné** (timeout, 5xx): portlet vykreslí chybovú JSP „SSO portál nedostupný“ + ERROR log.

## 7. SSO mock

Spring Boot appka, v compose za nginx pod `/sso/` (kontext path `/sso`).

| Endpoint | Správanie |
|---|---|
| `GET /sso/login?returnUrl=` | stránka s výberom operátora a zákazníka (pevný zoznam) |
| `POST /sso/login` | vytvorí portálovú session (UUID v `ConcurrentHashMap`), cookie `SSO_ID` (`Path=/`, `HttpOnly`), redirect na `returnUrl` (len relatívne URL – ochrana pred open redirect) |
| `POST /sso/customer` | zmení zákazníka v portálovej session, redirect späť |
| `POST /sso/logout` | zruší portálovú session, zmaže cookie, redirect na login |
| `GET /sso/api/sessions/{id}` | `200` JSON `{"operator":{"login","displayName"},"customer":{"customerId","displayName"}}` alebo `404` |

Mock **nikdy nevytvára `HttpSession`** (`server.servlet.session` sa nepoužíva, žiadne `@SessionAttributes`):
za jednou adresou by jeho `JSESSIONID` prepísal Liferay session (cookies nerozlišujú port ani aplikáciu).

Demo dáta (vymyslené):
- operátori: `citatel` (Čitateľ – `BLACKLIST_VIEW`), `spravca` (Správca – `BLACKLIST_VIEW`, `BLACKLIST_MODIFY`, `DEACTIVATION`);
  privilégiá nepozná SSO, ale `AclService` v CRM (ako v legacy: vlastné ACL);
- zákazníci: 2–3 (napr. `C-1001`, `C-1002`) s produktmi v in-memory backende.

## 8. Session fasáda a duálna session

### 8.1 Kontrakt `SessionManagerCustom`
Singleton (Spring bean v root kontexte), request si berie z `RequestAwareManager` – ako legacy.

| Skupina | Metódy | Zdroj |
|---|---|---|
| identita | `getOperator()`, `getCustomer()` (vracajú `null`, ak nie je – legacy štýl, Java 8 bez `Optional` v API fasády) | SSO portál; cache v atribúte requestu |
| hlášky | `pushInfo(key)`, `pushError(key)`, `popMessages()` | `SessionStore` |
| všeobecné | `setGlobalAttribute(key, value)`, `getGlobalAttribute(key)`, `removeGlobalAttribute(key)`, `flushAll()` | `SessionStore` |

### 8.2 `SessionStore`
- `PortletSessionStore` – `PortletSession`, `APPLICATION_SCOPE`, kľúče s prefixom `APP_`;
  po každej zmene znova `setAttribute` (mutácia zoznamu „na mieste“ by sa do Redisu nezreplikovala).
- `RedisSessionStore` – Jedis pool, hash `crm:session:{SSO_ID}`, hodnoty JSON, TTL 30 min predlžované pri prístupe.
- `DualSessionStore` – **zápis do oboch**, **čítanie podľa flagu**.
- `SharedKeys` – register kľúč → trieda pre JSON (de)serializáciu. Do zdieľanej session idú len jednoduché DTO;
  žiadna Java serializácia doménových tried (Java 8 vs. Java 21, `javax` vs. `jakarta`).

### 8.3 Feature flag
Kľúč v Redise `crm:flags:session-read-source` = `PORTLET` (default, aj keď kľúč chýba) | `REDIS`.
Číta sa pri každom requeste (raz, cache v atribúte requestu). Prepína sa `redis-cli SET …` bez reštartu
a bez deployu; ten istý flag bude čítať aj Spring appka v podprojekte 2.

### 8.4 Kľúč zdieľanej session = `SSO_ID`
Obe appky cookie `SSO_ID` majú; netreba vlastnú cookie (a odpadá problém, že portlet nevie nastaviť cookie
v render fáze). Odhlásenie v portáli ukončí aj zdieľanú session (nové `SSO_ID` = nový hash).

### 8.5 Čo sa zdieľa
Len to, čo ide cez fasádu: hlášky, `APP_BLACKLIST_LAST_CRITERIA` (posledné kritériá vyhľadávania),
`APP_LAST_VIEWED_RECORD` (posledný otvorený záznam blacklistu). `@SessionAttributes` modely ostávajú
v `PortletSession` `PORTLET_SCOPE` mimo fasády – to je pre migráciu podstatné rozlíšenie.

### 8.6 Chyby
| Situácia | Správanie |
|---|---|
| zápis do Redisu zlyhá | ERROR log, request pokračuje (pri `PORTLET` je zdrojom pravdy stará session) |
| čítanie pri `REDIS` zlyhá | fallback na `PortletSession` + ERROR log |
| neznáma hodnota flagu | `PORTLET` + WARN log |
| SSO vráti 404 / chýba `SSO_ID` | identita `null` → výzva na prihlásenie (kap. 6) |
| SSO nedostupné | chybová JSP v portlete + ERROR log |

## 9. Portlety

### 9.1 Spoločné
- **Backend:** rozhrania (porty) + in-memory implementácie v `customercare-portlets` (`…frontend.backend`),
  definované v `backend-context.xml`, ktorý importuje root `applicationContext.xml` (ekvivalent legacy
  kontextu, ktorý ťahá backend do toho istého JVM). Dáta sú spoločné pre oba portlety, nasadené pri štarte.
- **ACL:** `AclService` (login operátora → privilégiá, in-memory). `DispatcherPortletCustom` overí privilégium
  portletu z `init-param` (`requiredPrivilege`); jemnejšie kontroly `BaseCrmController.checkPrivilege(…)`
  a JSP tagy `common:aclPageFilterTag` / `common:aclFilter`.
- **Chyby v portlete:** `SimpleMappingExceptionResolverCustom`: `AclDeniedException` → `error/403.jsp`,
  `NotFoundException` → `error/404.jsp`, `SsoUnavailableException` → `error/sso.jsp`.
  Hooked JSP 403/404 (`custom-jsp-dir`) riešia chyby na úrovni portálu.
- **Identita v Liferay:** používateľ je pre portál vždy **guest**; operátora a zákazníka pozná len fasáda.
  Startup hook dá hosťovi VIEW na stránky aj portlety.
- **Kontext Spring:** každý portlet má `<portlet>-portlet.xml` (component-scan svojho balíka,
  `InternalResourceViewResolver` → `/WEB-INF/jsp/<portlet>/`, exception resolver).

### 9.2 Vlastné taglibs (rozhranie ako legacy)
- `portal:actionURL`, `portal:renderURL`, `portal:resourceURL` (+ `portal:param`): vytvoria URL cez
  `MimeResponse.create*URL()` a pretypujú na `LiferayPortletURL` (presne skrytá Liferay závislosť z analýzy);
  atribúty `var`, `escapeXml`, `id` (resource), `copyCurrentRenderParameters`.
- `common:message key [var] [includeKey]` + `common:msgarg` → `MessageSourceCustom` (`messages.properties`,
  slovensky; v legacy DB – komentár). Chýbajúci kľúč → `??key??` + WARN log.
- `common:aclPageFilterTag privilegeType wireframe` (zastaví render stránky bez privilégia),
  `common:aclFilter privilegeType` (telo len s privilégiom).

### 9.3 A – portlet `blacklist` (stránka `/web/crm/blacklist`)
Controller sa vyberá parametrom `viewPage`, metóda parametrom `action` / `render` (ako legacy).
Doména a pravidlá ako v spec „po“ §4 (`BlacklistRecord`, stavy, dôvody, typy podmienok, identifikátory, poznámky).

| Controller (`params` triedy) | Mapovanie | Poznámka |
|---|---|---|
| `BlacklistSearchController` (default) | render `search`; action `searchAction`, `clearFieldsAction`, `gotoCreateAction` | kritériá v `@SessionAttributes("searchCriteria")` + kópia do fasády `APP_BLACKLIST_LAST_CRITERIA`; bez výsledku → `pushInfo` + späť na search |
| `BlacklistResultController` (`viewPage=searchresult`) | render | tabuľka, odkaz na detail cez `portal:renderURL` |
| `BlacklistDetailController` (`viewPage=detail`) | render; action `changeAction`, `addNoteAction`; resource `removeIdentAction` (HTML fragment cez `ModelAndView`) | zápis `APP_LAST_VIEWED_RECORD`; zmeny len s `BLACKLIST_MODIFY` |
| `BlacklistCreateController` (`viewPage=create`) | render; resource `refreshCreateConditionTypes` (fragment `<select>`); action `createAction`, `gotoSearchAction` | **vlastná trieda Spring `Validator`** (`BlacklistCreateValidator`), chyby späť do formulára |

Navigačné akcie (`goto*`, `clearFields*`) v action fáze len nastavia render parameter – ako legacy
(pri migrácii z nich budú odkazy).

### 9.4 B – portlet `dashboard` (stránka `/web/crm/dashboard`)
Doména a pravidlá dátumov ako v spec „po“ §5 (`CustomerProduct`, `DeactivationType`, `DeactivationRequest`).

| Mapovanie | Správanie |
|---|---|
| `@RenderMapping` (default) | produkty zákazníka zo SSO session |
| `@RenderMapping(params = "page=tariffDeactivation")` + render param `productId` | formulár; `@ModelAttribute("deactivationForm")` + `@SessionAttributes("deactivationForm")` – **vedomá reprodukcia legacy**: pri otvorení formulára pre iný produkt ostane zastaraný stav (komentár; oprava až v migrácii) |
| `@ResourceMapping("earliestDate")` | JSON `{"earliestDate":"YYYY-MM-DD"}` |
| `@ActionMapping(params = "action=submit")` | validácia, uloženie žiadosti, `pushInfo`, `status.setComplete()`, render param `page=tariffDeactivationDone` |
| `@RenderMapping(params = "page=tariffDeactivationDone")` | potvrdenie |

Produkt iného zákazníka → `NotFoundException` (404 v portlete). Privilégium portletu: `DEACTIVATION`.

### 9.5 Portlet `header` (kompozícia stránky)
Legacy skladá stránku z viacerých portletov (hlavička, kontext zákazníka, info hlášky, obsah). Demo to ukazuje
jedným portletom `header`, ktorý theme vkladá na každú stránku (`$theme.runtime`):
- operátor a zákazník z fasády; odkazy na zmenu zákazníka a logout v SSO mocku; bez SSO session výzva „Prihlásiť“;
- hlášky z `popMessages()` – vykreslia sa raz. Liferay renderuje portlety stránky pred theme a action fáza
  prebehne pred všetkými rendermi, takže hláška z action fázy obsahového portletu sa zobrazí v tom istom
  nasledujúcom renderi (overiť testom na bežiacom stacku; ak by poradie nesedelo, zdokumentovať a hlášky
  vykresliť v obsahovom portlete).
Privilégium sa nevyžaduje (vidí ho aj neprihlásený).

## 10. Startup hook, hooked JSP, theme
- `liferay-hook.xml`: `portal.properties` s `application.startup.events` → `CrmStartupAction`
  (idempotentne: site „CRM“ s friendly URL `/crm`, verejné stránky `blacklist` a `dashboard` s portletmi,
  theme `customercare`, guest VIEW na stránky a portlety). Používa `*LocalServiceUtil` ako legacy hook.
- `custom-jsp-dir`: prepis portálových chybových stránok 403/404 (neutrálny vzhľad, bez brandingu).
- `customercare-theme`: minimálny Velocity theme (navigácia medzi stránkami, neutrálne CSS), vkladá portlet `header` (9.5).

## 11. Docker
- `docker/liferay/Dockerfile`: `FROM azul/zulu-openjdk:8u252`; stiahne bundle
  `liferay-portal-tomcat-6.2-ce-ga6-20160112152609836.zip` z `releases-cdn.liferay.com` a overí SHA-256
  `930f3969a19dd2bdd847dbdd48e8176fa4884ef2608f5fec38efcad9b279dc8c`; skopíruje `portal-ext.properties`
  a shaded `sso-portal-api` JAR do `tomcat/lib/ext` (preto build image po builde Mavenu).
- `portal-ext.properties`: `setup.wizard.enabled=false`, HSQL, auto-deploy dir. System properties pre
  SSO (`sso.portal.api-url=http://sso-mock:8090/sso`, `sso.portal.login-url=/sso/login`) a Redis (`crm.redis.host=redis`)
  idú cez `CATALINA_OPTS` v compose (náš kód ich číta ako system properties, nie cez Liferay `PropsUtil`).
- Zdieľaná session viditeľná naživo: RedisInsight (`redis/redisinsight:3.6.0`, port 5540), skripty
  `show-session` a `session-flag`, ladiaci riadok v hlavičke (zdroj čítania + `SSO_ID`).
- `docker-compose.yml`: `liferay` (volume pre `data/`, mount `docker/deploy/`), `redis`, `sso-mock`
  (build z `sso-mock/`), `nginx` (`:8000`; `/sso/` → sso-mock, `/` → Liferay; v podprojekte 2 pribudne `/crm/`).
- Skripty (PowerShell 5.1 + bash): `build-and-deploy` (Maven build `legacy/`, kopírovanie WAR-ov),
  `smoke` (kap. 12).

## 12. Testovanie

TDD po malých krokoch. Názvy `should_…_when…()` + `@DisplayName` po slovensky (jazyk repa).

- **`legacy/` (`mvn -f legacy/pom.xml verify`):**
  - fasáda: identita zo SSO + cache na request, duálny zápis, flag `PORTLET`/`REDIS`/neznámy, fallback pri výpadku Redisu;
  - `PortletSessionStore` (prefix, opätovný `setAttribute`), `RedisSessionStore` (Jedis mock, TTL, JSON podľa `SharedKeys`);
  - `HttpPortalSessionManager` proti stubu z JDK `HttpServer` (200, 404, timeout, chýbajúca cookie);
  - tagy: URL tagy (mock `LiferayPortletURL`, parametre, `var`, `escapeXml`, chyba bez povinného segmentu),
    `common:message` (argumenty, chýbajúci kľúč), ACL tagy;
  - controllery A a B s portlet mockmi zo `spring-test` (render/action/resource, render parametre,
    `@SessionAttributes`, validátor, 403, 404), pravidlá dátumov, in-memory služby, ACL.
- **`sso-mock/` (`mvn -f sso-mock/pom.xml verify`):** MockMvc – login nastaví `SSO_ID` a **nevytvorí `HttpSession`**,
  API 200/404, zmena zákazníka, logout, odmietnutie absolútneho `returnUrl`.
- **Smoke test na bežiacom stacku** (`docker/smoke.*`, curl cez nginx, overené spustením):
  login v SSO → blacklist hľadanie → detail → zmena stavu → dashboard formulár → odoslanie → potvrdenie;
  kontrola kľúčov v Redise; prepnutie flagu na `REDIS` a zopakovanie čítania.

## 13. Dokumentácia
- `legacy/README.md`: moduly, spustenie, demo operátori, mapa „kde v deme ↔ kde v reálnom legacy“ (cez skratky `$FE`, `CP`, `FBC`, `J`).
- `poznamky/10-liferay62-v-dockeri.md` (nová): ako rozbehnúť Liferay 6.2 lokálne, pasce zo spiku.
- `poznamky/07-pasce.md`: ICU a Java 8 update > 255, kolízia `JSESSIONID` za jednou adresou, guest oprávnenia,
  render fáza nevie nastaviť cookie ani presmerovať.
- `poznamky/09-session-liferay-vs-spring.md`: duálna session je implementovaná – odkazy na súbory.
- `poznamky/README.md`: index; hlavný `README.md`: podprojekty a stav; `CLAUDE.md`: stav a build príkazy.
- Kód obsahuje komentáre s „prečo“ (legacy vzor, význam pre migráciu), nie opis toho, čo robí.

## 14. Mimo rozsahu
Spring Boot appka „po“ (podprojekt 2); vlastný dashboard dispatch a friendly URL; Liferay login a SSO autologin;
reálna DB (HSQL stačí), cluster; JBoss; ostatné legacy portlety, page-flow objednávky, CMS obsah, preklady z DB.

## 15. Git
Nič sa necommituje ani nepushuje – robí to používateľ. Pred odovzdaním prebehne anonymizačná kontrola
(`bash poznamky/LOCAL-anonymizacia.sh`, očakávaný výstup: nič).
