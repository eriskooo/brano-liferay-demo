# Spec: kópia `crm-web` v Ruste (podprojekt 3)

Dátum: 2026-10-14 · Stav: návrh na schválenie · Nadväzuje na: [`2026-10-12-crm-web-migration-design.md`](2026-10-12-crm-web-migration-design.md)
(podprojekt 2, Java appka `crm-web/`)

## 1. Cieľ

Funkčná **kópia 1:1** zmigrovaného blacklistu (`crm-web/`) napísaná v Ruste (`crm-web-rs/`), zabalená do Dockera a
spustená **súčasne** so všetkými ostatnými službami. Java `crm-web` ostáva **bez zmeny**.

Ukazuje, že strangler kontrakt (cookie `SSO_ID` + Redis hash `crm:session:{SSO_ID}` s JSON hodnotami) nezávisí od jazyka:
tri appky (Liferay, Java, Rust) vidia toho istého operátora, zákazníka a tú istú zdieľanú session.

Úspech:
- `http://localhost:8000/crm-rs/blacklist/search` vyzerá a správa sa rovnako ako `/crm/blacklist/search`
  (rovnaké stránky, formuláre, validácie, ACL, AJAX, PRG + hlášky),
- záznam otvorený v Rust appke ukáže v hlavičke Java appka aj Liferay (pri flagu `REDIS`); hláška zapísaná jednou appkou
  sa zobrazí práve raz v ktorejkoľvek,
- `bash docker/smoke-rs.sh` → `RS SMOKE OK`, existujúce `smoke.sh` a `smoke-migration.sh` prechádzajú ďalej.

Rozhodnutia používateľa (2026-10-14): `crm-web` sa zachová; Rust appka beží na **vlastnej ceste `/crm-rs/…`** (odkaz
„Blacklist“ v Liferay vedie ďalej na Javu); stack **axum + askama**; build a testy **len v Dockeri** (lokálny Rust nie je).

## 2. Technológie

| Vec | Voľba |
|---|---|
| Jazyk | Rust (stable, edícia 2024), presná verzia image `rust:<x.y>-slim` sa pripne v pláne |
| Web | `axum` (+ `tokio`, `tower`, `tower-http`), formuláre `axum::Form` / `serde` |
| Šablóny | `askama` – kompilované, typované; HTML escape automaticky |
| Redis | `redis` crate s `tokio-comp`, `aio::ConnectionManager`, timeout 500 ms (ako Java) |
| SSO klient | `reqwest` (rustls), timeouty 1 s connect / 2 s celkovo (ako Java) |
| JSON | `serde` + `serde_json`, neznáme polia sa ignorujú (default serde) |
| Logovanie | `tracing` + `tracing-subscriber` |
| Testy | `cargo test`; webové testy cez `tower::ServiceExt::oneshot` s fake `SsoApi` v pamäti; `SsoClient` proti axum serveru na náhodnom porte; Redis = dočasný kontajner zo `test.sh` |
| Runtime | Docker služba `crm-web-rs`, multi-stage build (`rust:*-slim` → `debian:*-slim`), počúva na 8080, na hoste **:8082**, prefix `/crm-rs` |

Verzie crate-ov sa pripnú v pláne (`Cargo.lock` sa commituje).

## 3. Zapojenie do stacku

- **Nový adresár `crm-web-rs/`** – samostatný Cargo projekt. `crm-web/`, `legacy/`, `sso-mock/` sa nemenia.
- **`docker/docker-compose.yml`** – nová služba:
  `crm-web-rs` (`build: context: .., dockerfile: crm-web-rs/Dockerfile`), env `CRM_SSO_APIURL=http://sso-mock:8090/sso`,
  `CRM_REDIS_HOST=redis`, `ports: ["8082:8080"]`, `depends_on: [redis, sso-mock]`; `nginx.depends_on` ju doplní.
- **Build kontext = koreň repa**, lebo appka pri kompilácii číta `crm-web/src/main/resources/messages.properties` (§4.3).
  `crm-web-rs/Dockerfile.dockerignore` povolí len zdroj `crm-web-rs/`, `messages.properties` a `static/` z crm-web – **`input/` sa do kontextu nesmie dostať**.
- **`docker/nginx/default.conf`** – `location /crm-rs/ { proxy_pass http://crm-web-rs:8080; … }` s rovnakými hlavičkami ako `/crm/`.
  Strangler presmerovanie `/web/crm/blacklist` → `/crm/…` ostáva.
- **SSO mock** sa nemení – `safeReturnUrl` prijme každú relatívnu cestu, teda aj `/crm-rs/…`.
- **`build-and-deploy.sh/.ps1`** – bez nového kroku buildu (Rust sa kompiluje pri `compose up --build`); len výpis adresy :8082.
- **`docker/smoke-rs.sh` + `.ps1`** – nový smoke test (§6.4).

## 4. Kontrakty zachované 1:1 s `crm-web`

### 4.1 Identita
- Cookie `SSO_ID`, platný tvar `[A-Za-z0-9-]{1,64}`, inak = neprihlásený.
- Identita sa načíta zo SSO API rovnakým volaním a mapovaním ako `SsoClient` v Jave, raz na request.
- Bez identity: stránka → `302 /sso/login?returnUrl=<url-encoded>`; pri GET je `returnUrl` aktuálna cesta + query,
  pri POST stránka „portletu“ (`/crm-rs/blacklist`); AJAX (`X-Requested-With: XMLHttpRequest`) → `401`.
- SSO nedostupné → stránka `error/sso` (ekvivalent `SsoUnavailableException`).

### 4.2 Zdieľaná session
- Redis hash `crm:session:{SSO_ID}`, zdieľané kľúče **len** `APP_MESSAGES`, `APP_BLACKLIST_LAST_CRITERIA`,
  `APP_LAST_VIEWED_RECORD` – rovnaký register ako `SharedSessionKeys`. Hodnoty = JSON s rovnakými menami polí ako
  Java records (`UiMessage`, `LastSearchCriteria`, `LastViewedRecord`), enumy ako reťazce.
- Hlášky: `push` zapíše vždy celý nový zoznam; `pop` vráti a zmaže kľúč (zobrazia sa práve raz, aj z inej appky).
- Ostatné (nezdieľané) kľúče: vlastná in-memory session s cookie `CRM_RS_SESSION`, `Path=/crm-rs`, `HttpOnly` – nesmie
  kolidovať s `CRM_SESSION` (Java, `/crm`) ani `JSESSIONID` (Liferay).
- Výpadok Redisu: `error!` log, fasáda sa správa ako prázdna, request nespadne (bez hlášok a posledného záznamu).

### 4.3 Správanie a vzhľad
- Routy (všetky s prefixom `/crm-rs`): `GET /`, `GET /blacklist` → redirect na vyhľadávanie (ako `HomeController`);
  `GET /blacklist/search`, `POST /blacklist/searchAction`, `GET /blacklist/searchresult`, `GET /blacklist/newSearch`,
  `GET /blacklist/detail`, `POST /blacklist/changeAction`, `POST /blacklist/addNoteAction`,
  `POST /blacklist/resource/removeIdentAction`, `GET /blacklist/create`,
  `GET /blacklist/resource/refreshCreateConditionTypes`, `POST /blacklist/createAction`.
- Rovnaké HTTP metódy, parametre, PRG redirecty, stavové kódy (403 ACL, 404 neznámy záznam, 400 zlý parameter) a
  validačné pravidlá formulárov ako Java verzia.
- ACL: `spravca` = `BLACKLIST_VIEW`, `BLACKLIST_MODIFY`, `DEACTIVATION`; `citatel` = `BLACKLIST_VIEW`; ostatní nič.
- HTML výstup štruktúrou zhodný s JSP (rovnaké `id`, `name`, CSS triedy, JS hooky v `crm.js`); statické `crm.css`,
  `portlet.css`, `theme.css`, `crm.js` sa **nekopírujú** – servíruje ich `tower-http::ServeDir` priamo z
  `crm-web/src/main/resources/static` (v Dockeri skopírované do image). Téma ide z Liferay cez nginx ako v Jave.
- Texty: `messages.properties` sa **nekopíruje** – načíta sa pri kompilácii (`include_str!`) z `crm-web`, jeden zdroj.
  `crm-web-rs/messages-rs.properties` prepíše len rozdiely: `app.name` („… (Rust)“), `header.newApp` („Redis (Rust appka)“
  – ladiaci riadok tak odlíši taby) a pridá `validation.invalid`. Formát `{0}` ako Java `MessageFormat` (v rozsahu súboru).
- Navigácia „Dashboard“ vedie do Liferay ako v Jave.
- Vedomá odchýlka: neplatný dátum/enum vo formulári → „Neplatná hodnota.“ (Java ukáže technickú hlášku Springu typeMismatch).
- Dáta: vlastný in-memory blacklist s **rovnakým seedom** ako `crm-web` (vedomé obmedzenie – zdieľa sa len session a identita).

## 5. Vnútorná štruktúra

```
crm-web-rs/
  Cargo.toml, Cargo.lock, Dockerfile, Dockerfile.dockerignore, test.sh, test.ps1, messages-rs.properties, README.md
  src/
    main.rs        – štart, logovanie, zostavenie Routera pod /crm-rs
    lib.rs         – `app(state) -> Router` (aby ho testy volali bez siete)
    config.rs      – konfigurácia z env (CRM_SSO_APIURL, CRM_SSO_LOGINURL, CRM_REDIS_HOST/PORT), defaulty ako application.properties
    acl.rs         – Privilege, trait AclService + InMemoryAclService
    session/       – identity.rs (SSO_ID → SsoClient), shared.rs (CrmSession fasáda), store.rs (trait SharedSessionStore
                     + RedisSharedSessionStore), keys.rs (register zdieľaných kľúčov + serde typy), local.rs (CRM_RS_SESSION)
    blacklist/     – model.rs, service.rs (trait + InMemory, seed), forms.rs (formuláre + validate), search.rs,
                     detail.rs, create.rs (handlery)
    web/           – portlet.rs (extraktory Identity / Require<P> / CrmSession), layout.rs (LayoutModel),
                     errors.rs (AppError + IntoResponse), messages.rs (properties + formát), tags.rs (URL a form helpery)
  templates/       – layout/, blacklist/ (+ fragments/), error/  (.html pre askama)
  tests/           – webové a integračné testy
```

Každý modul má jednu zodpovednosť a závislosti cez traity (`AclService`, `SharedSessionStore`, `BlacklistService`,
`IdentityResolver`, `Clock`) – v testoch sa nahradia in-memory/fake implementáciou.

### 5.1 Mapovanie Java → Rust

| Java (Spring) | Rust (axum + askama) |
|---|---|
| `@Controller` + `@GetMapping`/`@PostMapping` | handler funkcie + `Router::route` |
| `@LegacyPortlet("/blacklist")` + `LegacyPortletInterceptor` | extraktor `Identity` (zlyhanie → SSO redirect / 401); base path portletu je konštanta modulu |
| `@RequiresPrivilege` | typový extraktor `Require<Modify>` – privilégium je v signatúre handlera |
| `CrmSessionArgumentResolver` | extraktor `CrmSession` |
| `LayoutModelInterceptor` | `LayoutModel` z extraktora, pole v každej šablóne stránky |
| `GlobalExceptionHandler` | `enum AppError { NotFound, AclDenied, BadRequest, SsoUnavailable, Rule }` + `IntoResponse` (AJAX → fragment) |
| `portal:renderURL/actionURL/resourceURL` + `portal:param` | funkcie `render_url`, `action_url`, `resource_url` s rovnakým skladaním `{ctx}{base}/{segment}` a URL-encode parametrov |
| `common:message` / `common:msgarg` | filter `msg` / funkcia `msg_args` |
| `common:aclFilter` / `common:aclPageFilterTag` | `layout.can(Privilege)` v `{% if %}`, resp. makro s rovnakým menom |
| `form:form/input/select/option/textarea/errors` | askama makrá generujúce rovnaké `name`/`id`/triedy a chybové `<span>` ako Spring tagy |
| Bean Validation na formoch | ručná `validate(&self) -> FieldErrors` |
| `RedisTemplate` / `StringRedisTemplate` | `ConnectionManager`, `HGET`/`HSET`/`HDEL` |
| `RestClient` (SSO) | `reqwest::Client` |
| `ClockConfig` | `trait Clock` (`SystemClock`, v testoch fixný) |

Identifikátory anglicky, komentáre **po slovensky** – vysvetľujú rozhodnutie a ekvivalent v Jave/legacy, nie čo kód robí.
Žiadne `unwrap()`/`expect()` na vstupe od používateľa ani na I/O za behu (len pri štarte, ak chýba povinná konfigurácia).

## 6. Testy

Rust toolchain nie je lokálne – testy bežia v Dockeri.

### 6.1 Spúšťanie
`bash crm-web-rs/test.sh` (+ `test.ps1`): vytvorí dočasnú sieť a kontajner `redis:7.4-alpine`, spustí
`cargo test` v image `rust:<x.y>-slim` s pripojeným zdrojom a volumami pre cargo registry a `target/`
(opakovaný beh bez sťahovania), na konci kontajner a sieť zmaže. `REDIS_URL` dostanú testy z env;
ak chýba, Redis testy sa preskočia s varovaním (ostatné bežia). Náhrada Testcontainers z Java verzie.

### 6.2 Unit testy (`#[cfg(test)]` v moduloch)
ACL, validácia formulárov, `InMemoryBlacklistService` (pravidlá, seed), skladanie URL a escapovanie parametrov,
`msg` / `msg_args`, parsovanie a validácia `SSO_ID`, `SharedCrmSession` nad in-memory store (push/pop, lokálne vs
zdieľané kľúče, výpadok store).

### 6.3 Kontraktové a webové testy (`tests/`)
- **Kontrakt JSON:** serializácia `UiMessage`, `LastSearchCriteria`, `LastViewedRecord` sa porovná s doslovným JSON,
  aký zapisuje Java verzia; deserializácia s neznámym poľom prejde.
- **Web (ekvivalent MockMvc):** `app(state).oneshot(request)` s fake `SsoApi` (bez siete) a in-memory store – vyhľadávanie
  (PRG, uložené kritériá), výsledky, detail + posledný záznam, zmena stavu, poznámka, AJAX odstránenie identifikátora
  (len POST), create + validačné chyby, ACL 403 pre `citatel`, 401 pre AJAX bez identity, redirect na SSO, 404, hláška práve raz.
- **Redis store** proti skutočnému Redisu (z `test.sh`).
- Názvy `should_x_when_y`, nad každým testom `///` komentár s popisom po slovensky (ekvivalent `@DisplayName`).
  Pokrytie: happy path, chýbajúci/neplatný vstup, prázdne kolekcie, hraničné hodnoty.

### 6.4 E2E – `docker/smoke-rs.sh` (+ `.ps1`)
Proti bežiacemu stacku cez :8000: prihlásenie v SSO mocku (`spravca`, `C-1001`) → Rust vyhľadávanie → detail →
v Redise je `APP_LAST_VIEWED_RECORD` zapísaný Rust appkou → Java `/crm/blacklist/search` ho ukáže v hlavičke →
zmena stavu v Rust → hláška sa zobrazí raz → `citatel` dostane na POST 403. Na konci `RS SMOKE OK`.

## 7. Dokumentácia (po slovensky)

- `crm-web-rs/README.md` – spustenie, testy, preklikanie (dva taby Java/Rust so zdieľanou session), tabuľka „koncept → súbor“.
- `docs/RUST.md` – porovnanie Java vs Rust: mapovacia tabuľka (§5.1), počet riadkov kódu, veľkosť image, RAM v pokoji
  a čas štartu (namerané po builde, nie odhad), čo Rust prináša (typované šablóny, privilégium v signatúre) a čo stojí
  (dlhší build, vlastné form helpery). Hlavné poučenie: strangler kontrakt je nezávislý od jazyka.
- `poznamky/` – krátka poznámka „zdieľaná session/identita pre appku v inom jazyku“ (čo musí dodržať: kľúče, JSON,
  ignorovanie neznámych polí, vlastná session cookie s cestou) + odkaz v `poznamky/README.md`.
- `CLAUDE.md` – stav, podprojekt 3, build & run (`test.sh`, `smoke-rs.sh`), služba v runtime architektúre.
- Pred odovzdaním: `bash poznamky/LOCAL-anonymizacia.sh` bez výstupu.

## 8. Mimo rozsahu

Dashboard; zdieľanie dát blacklistu medzi appkami; zmena `crm-web`, `legacy`, `sso-mock`; prepínač v nginx medzi
Java a Rust; lokálna Rust toolchain; CI.

## 9. Riziká

| Riziko | Opatrenie |
|---|---|
| HTML sa odchýli od JSP (Spring form tagy generujú `id`, skryté polia, triedy chýb) | webové testy kontrolujú kľúčové `name`/`id`; v bežiacom stacku `diff` normalizovaného HTML Java a Rust stránok |
| JSON kontrakt sa rozíde (formát dátumu, enumy, `null` vs chýbajúce pole) | kontraktové testy s doslovným JSON z Java verzie; smoke-rs overuje cez skutočný Redis |
| Pomalý prvý build v Dockeri | cache vrstvy pre závislosti (`cargo fetch` / prázdny `main.rs` pred kopírovaním zdroja), volumy v `test.sh` |
| Build kontext = koreň repa zatiahne `input/` alebo `target/` | `Dockerfile.dockerignore` s allowlistom (`*` + výnimky) |
