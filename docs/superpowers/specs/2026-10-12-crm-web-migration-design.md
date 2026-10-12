# Spec: migrácia portletu blacklist na Spring Boot a súbeh so starou appkou (podprojekt 2)

Dátum: 2026-10-12 · Stav: návrh na schválenie · Nahrádza: [`2026-10-08-liferay-migracia-demo-design.md`](2026-10-08-liferay-migracia-demo-design.md)
(jeho nezmenené časti sa preberajú, viď kap. 3) · Nadväzuje na: [`2026-10-12-legacy-liferay-app-design.md`](2026-10-12-legacy-liferay-app-design.md)
(podprojekt 1, stará appka v `legacy/`)

## 1. Cieľ

Ukázať migráciu **počas prevádzky**: portlet **blacklist ne-zákazníkov** sa presunie zo starej appky (Liferay 6.2,
`legacy/`) do novej Spring Boot appky (`crm-web/`), **dashboard ostáva v Liferay**. Obe appky bežia súčasne za jedným
nginx (strangler) a zdieľajú:
- **identitu** – operátor a zákazník zo SSO mocku (cookie `SSO_ID`),
- **session** – hash `crm:session:{SSO_ID}` v Redise (hlášky, posledné kritériá, posledný otvorený záznam).

Úspech = v prehliadači prechádzaš medzi novým blacklistom a starým dashboardom a stav sa prenesie; `show-session`
ukáže, že do jedného hashu píšu obe appky; prepnutie flagu `PORTLET` ↔ `REDIS` viditeľne mení, čo stará appka vidí.

Rozhodnutia používateľa (2026-10-12): migruje sa **blacklist**; prepnutie **strangler cez nginx**; nový spec + plán
(tento), spec 2026-10-08 je nahradený.

## 2. Technológie

| Vec | Voľba |
|---|---|
| Java / framework | 21, Spring Boot **4.1.1** (`spring-boot-starter-webmvc`, `-validation`, `-data-redis`), WAR + `tomcat-embed-jasper` + Jakarta JSTL (overené v spec 2026-10-08) |
| SSO klient | Spring `RestClient`, timeouty 1 s connect / 2 s read |
| Redis | Spring Data Redis (Lettuce), `StringRedisTemplate`, JSON cez Jackson (Boot BOM) |
| Testy | JUnit 5, MockMvc, `@SpringBootTest(RANDOM_PORT)`, **Testcontainers** (`redis:7.4-alpine`) pre Redis, JDK `HttpServer` ako fake SSO |
| Projekt | `crm-web/` – samostatný Maven projekt, `groupId=com.example.crm`, `artifactId=crm-web`, balík `com.example.crm` |
| Runtime | Docker služba `crm-web` (image `eclipse-temurin:21-jre`, WAR z `crm-web/target`), kontext `/crm`, port 8081 aj priamo na hoste |

## 3. Čo sa preberá zo specu 2026-10-08 (bez zmeny princípu)

- Mapovanie fáz: render → `GET`, action → `POST` + redirect (PRG), resource → AJAX endpoint (HTML fragment).
- Tagy `portal:*` / `common:*` s **rovnakým rozhraním** ako legacy, implementované bez Liferay (§3.4, 3.5 starého specu):
  URL sa skladajú ako `{ctx}{base}/{action|render|viewPage}` resp. `{ctx}{base}/resource/{id}`; chýbajúci segment = `JspException`.
- `@LegacyPortlet("/blacklist")` + interceptor (base path pre tagy, `@RequiresPrivilege`, AJAX bez prihlásenia → 401).
- `GlobalExceptionHandler`: ACL → 403, `NotFoundException` → 404, zlý parameter → 400.
- Blacklist: doména, pravidlá, stránky a endpointy podľa §4 starého specu, **Bean Validation** namiesto vlastných `Validator`
  tried, **seed dáta zhodné so starou appkou** (`legacy/.../InMemoryBlacklistService`).
- Navigačné „goto“ akcie → obyčajné odkazy.

Vypúšťa sa zo starého specu: fake login (§3.2), feature B / dashboard (§5), čistá `HttpSession` ako jediné úložisko (§3.1),
Boot appka v koreni repa.

## 4. Architektúra a smerovanie

```
prehliadač ─► nginx :8000
   ├─ /web/crm/blacklist (aj s parametrami portletu) ─► 302 /crm/blacklist/search   (strangler)
   ├─ /crm/   ─► crm-web:8080 (Spring Boot, kontext /crm)
   ├─ /sso/   ─► sso-mock:8090
   └─ /       ─► liferay:8080 (dashboard, hlavička, admin)
crm-web ─► sso-mock REST /sso/api/sessions/{SSO_ID}     crm-web ─► Redis crm:session:{SSO_ID}
```

- Starý blacklist ostáva dostupný **priamo na :8080** (`/web/crm/blacklist`) na porovnanie „pred/po“.
- `crm-web` má vlastnú session cookie **`CRM_SESSION`** s `Path=/crm` – nekoliduje s `JSESSIONID` Liferay.
- `GET /crm` a `GET /crm/blacklist` → redirect na `/crm/blacklist/search`.

## 5. Zdieľaná session a identita v `crm-web`

### 5.1 `CrmSession` (kontrakt ako legacy `SessionManagerCustom`)
`getOperator()`, `getCustomer()` (Optional – nový kód, Java 21), `getLoginUrl(returnPath)`, `pushInfo/pushError/popMessages`,
`setGlobalAttribute/getGlobalAttribute/removeGlobalAttribute`. Controllery ho dostanú ako parameter metódy
(`HandlerMethodArgumentResolver`), implementácia je per-request obal nad `HttpServletRequest`.

### 5.2 Identita
- `SsoClient` volá `GET {crm.sso.api-url}/api/sessions/{SSO_ID}` (default `http://sso-mock:8090/sso`); `SSO_ID` musí
  zodpovedať `[A-Za-z0-9-]{1,64}`, inak „neprihlásený“ bez HTTP volania.
- Výsledok sa cachuje v atribúte requestu. 404 = neprihlásený; timeout/5xx = `SsoUnavailableException` → stránka „SSO nedostupné“ (503).
- Bez identity: stránka → 302 `/sso/login?returnUrl={cesta}`; AJAX (`X-Requested-With`) → 401 bez tela.
- Setter operátora/zákazníka neexistuje – identitu vlastní SSO portál.

### 5.3 Úložisko
- **Zdieľané kľúče** (len tieto) idú do **Redisu**, hash `crm:session:{SSO_ID}`, TTL 1800 s predlžované pri prístupe,
  hodnoty JSON v **rovnakom tvare ako stará appka** (kontrakt medzi Java 8 a Java 21):
  - `APP_MESSAGES` – `[{"level":"INFO|ERROR","key":"…"}]`
  - `APP_BLACKLIST_LAST_CRITERIA` – `{"firstName","lastName","identifierValue","state"}` (reťazce / null)
  - `APP_LAST_VIEWED_RECORD` – `{"id":<long>,"fullName":"…"}`
- Nová appka **nemá starú session**, zdieľané kľúče preto číta **vždy z Redisu** (flag `crm:flags:session-read-source`
  len zobrazuje v ladiacom riadku). Ostatné (formuláre v `@SessionAttributes`) sú v `HttpSession` a nezdieľajú sa.
- Výpadok Redisu: zápis sa zaloguje (ERROR, bez pádu requestu), čítanie vráti prázdno – stránka funguje bez hlášok
  a posledného záznamu.
- **Poučenie, ktoré demo ukazuje:** aby stará appka videla, čo zapísala nová, musí mať flag **`REDIS`** – prepína sa
  najneskôr s nasadením prvej zmigrovanej stránky.

## 6. Hlavička a layout `crm-web`

`WEB-INF/jsp/layout/header.jspf` + `footer.jspf` (náhrada theme + portletu `header`): operátor, zákazník, „Naposledy
otvorený záznam“, hlášky (raz), ladiaci riadok (`Zdieľaná session: zdroj čítania = Redis (nová appka), flag starej appky = …,
SSO_ID = …`), navigácia **Blacklist** (`/crm/blacklist/search`) a **Dashboard** (`/web/crm/dashboard` – Liferay),
„Zmeniť zákazníka“ (`/sso/login?returnUrl=…`) a „Odhlásiť“ (`POST /sso/logout`). Vzhľad zhodný so starou appkou
(Bootstrap 2 triedy `table`, `btn` – CSS sa načíta z Liferay `/html/themes/classic/css/…` cez nginx, aby obe časti
vyzerali rovnako; ak Liferay nebeží, vlastné `crm.css` stačí na čitateľnosť).

## 7. Blacklist v `crm-web` – URL

Base `/crm/blacklist` (`@LegacyPortlet("/blacklist")`, kontext `/crm`).

| Legacy | Nové | Privilégium |
|---|---|---|
| default render (`search`) | `GET /search` | VIEW |
| action `searchAction` → `viewPage=searchresult` | `POST /searchAction` → 302 `GET /searchresult` | VIEW |
| `viewPage=detail&detailId=` | `GET /detail?detailId=` (zapíše `APP_LAST_VIEWED_RECORD`) | VIEW |
| action `changeAction`, `addNoteAction` | `POST /changeAction`, `POST /addNoteAction` → 302 na detail | MODIFY |
| resource `removeIdentAction` | `POST /resource/removeIdentAction` (GET → 405) | MODIFY |
| `viewPage=create` | `GET /create` | MODIFY |
| resource `refreshCreateConditionTypes` | `GET /resource/refreshCreateConditionTypes?stateReason=` | MODIFY |
| action `createAction` | `POST /createAction` → 302 na detail / formulár s chybami | MODIFY |

Kritériá vyhľadávania: `@SessionAttributes` (HttpSession) + kópia do `APP_BLACKLIST_LAST_CRITERIA`. Hlášky ako legacy.

**Backend (vedomé obmedzenie):** `crm-web` má vlastnú in-memory kópiu dát (rovnaký seed). Legacy backend beží v JVM
Liferay a nová appka ho zdieľať nemôže (riziko č. 2 analýzy) – zmeny sa medzi appkami neprenášajú, prenáša sa len session.
`MIGRACIA.md` vysvetlí, že reálne by obe appky išli na ten istý backend (adaptér cez REST/DB).

## 8. Opravy starej appky (z review podprojektu 1)

1. **Vlastník PortletSession:** `PortletSessionStore` si uloží `APP_SSO_OWNER`; ak sa `SSO_ID` requestu líši, zmaže
   všetky `APP_*` kľúče a formuláre v `@SessionAttributes` (invalidácia PortletSession), aby operátor B nevidel stav A.
2. **`removeIdentAction` len cez POST** (resource fáza bez `p_auth`; GET vráti 405).
3. **README krok 6** – pasca `@SessionAttributes` sa ukáže po odoslaní, ktoré **neprejde validáciou**.
4. `smoke.sh` testuje starý blacklist na **:8080** (na :8000 ho presmeruje nginx); ostatné kroky bez zmeny.

## 9. Testovanie

- **`crm-web` unit:** tagy (URL, i18n, ACL), `CrmSession` (cache identity, hlášky raz po redirecte, zdieľané kľúče len
  do Redisu, výpadok Redisu), `SsoClient` proti JDK `HttpServer` (200/404/timeout/poškodená cookie), doména blacklistu.
- **MockMvc:** routy, PRG, `@SessionAttributes`, Bean Validation, 403 čitateľa, 401 AJAX bez SSO, redirect na SSO login,
  `removeIdent` GET → 405, escapovanie HTML vo vstupe.
- **Integračné** (`RANDOM_PORT`, Tomcat + Jasper, fake SSO, Redis cez Testcontainers): vyhľadávanie → detail → v Redise
  je `APP_LAST_VIEWED_RECORD` presne v JSON tvare, ktorý číta stará appka; hláška po PRG zobrazená raz.
- **`legacy/`:** testy opráv z kap. 8; `mvn verify` a `smoke.sh` zelené.
- **Na bežiacom stacku** – `docker/smoke-migration.sh` (+ `.ps1` spúšťač) cez nginx:
  1. `/web/crm/blacklist` → 302 `/crm/blacklist/search`;
  2. SSO login → hlavička novej appky s operátorom a ladiacim riadkom;
  3. vyhľadávanie (aj diakritika) → detail → zmena stavu → hláška raz; AJAX bez SSO → 401; čitateľ → 403;
  4. Redis hash obsahuje `APP_LAST_VIEWED_RECORD` z novej appky;
  5. flag `PORTLET` → Liferay dashboard **neukáže** posledný záznam;
  6. flag `REDIS` → Liferay dashboard ukáže **„Naposledy otvorený záznam: Peter Vzorka“**;
  7. zmena zákazníka v SSO sa prejaví v oboch appkách.

## 10. Dokumentácia

- `crm-web/README.md` – spustenie, **návod na preklikanie súbehu** (vrátane `show-session` a prepínania flagu).
- `docs/MIGRACIA.md` – pre každý koncept „pred“ (odkaz na súbor v `legacy/`) → „po“ (odkaz do `crm-web/`) → prečo;
  na konci mapovacia tabuľka (rozšírená `poznamky/05`).
- `poznamky/` – pasce z implementácie (07), súbeh v praxi (09), odkazy na demo v playbooku (06), index.
- `README.md`, `CLAUDE.md` – stav, štruktúra, príkazy; spec 2026-10-08 v hlavičke označený ako nahradený.

## 11. Mimo rozsahu

Zdieľaný backend (len vysvetlenie), migrácia dashboardu, Spring Security, cluster, prepínanie strangleru za behu
(len poznámka v dokumentácii).

## 12. Git

Commit robí asistent na pokyn používateľa (povolené v tejto session); **nikdy push**. Pred pushom anonymizačná kontrola.
