# Rust kópia novej appky – porovnanie s Java verziou

Podprojekt 3 dema: ten istý zmigrovaný blacklist ako `crm-web/` (Spring Boot 4.1.1 + JSP), napísaný v Ruste
(`crm-web-rs/`, axum 0.8 + askama 0.16). Spec a plán: `docs/superpowers/*/2026-10-14-crm-web-rs*`.

## Prečo

Overiť, že strangler kontrakt **nezávisí od jazyka**. Tretia appka v inom jazyku sa pripojí ku Liferay a Java appke
bez jedinej zmeny v nich – stačí dodržať cookie `SSO_ID`, SSO API a JSON v Redis hashi `crm:session:{SSO_ID}`.

## Ako to beží

```
prehliadač → nginx :8000 ─┬─ /web/…    → Liferay 6.2 (legacy, dashboard)
                          ├─ /crm/…    → crm-web (Java, blacklist)
                          ├─ /crm-rs/… → crm-web-rs (Rust, ten istý blacklist)   ← nové
                          └─ /sso/…    → SSO mock
všetky tri appky ─→ SSO API (identita z cookie SSO_ID)
všetky tri appky ─→ Redis hash crm:session:{SSO_ID} (hlášky, posledné kritériá, posledný záznam)
```

- Odkaz „Blacklist“ v Liferay vedie ďalej na Java appku; Rust appka je dostupná na vlastnej ceste (a priamo na :8082).
- Vlastná session cookie `CRM_RS_SESSION` s `Path=/crm-rs` – nebije sa s `CRM_SESSION` (Java) ani `JSESSIONID` (Liferay).
- Texty (`messages.properties`) aj CSS/JS sa berú **priamo z crm-web** – jeden zdroj. Rust prepíše len rozdiely
  (`messages-rs.properties`: názov appky, ladiaci riadok, `validation.invalid`). Docker build preto má kontext
  koreň repa a allowlist v `crm-web-rs/Dockerfile.dockerignore` (kontext ~200 kB, `input/` sa doň nedostane).
- Každá appka má vlastné in-memory dáta blacklistu (rovnaký seed); zdieľa sa len identita a session.

## Mapovanie Java → Rust

| Java (Spring) | Rust (axum + askama) |
|---|---|
| `@Controller` + `@GetMapping`/`@PostMapping` | handler funkcie + `Router::route` (`src/blacklist/*.rs`) |
| `@LegacyPortlet` + `LegacyPortletInterceptor` (SSO → login/401) | extraktor `Ctx` (`src/web/portlet.rs`) |
| `@RequiresPrivilege` | typový extraktor `Require<View>` / `Require<Modify>` v signatúre handlera |
| `CrmSessionArgumentResolver`, `CrmSession` | `ctx.session` (`src/session/shared.rs`) |
| `@SessionAttributes`, `HttpSession` | `LocalSessions` (`src/session/local.rs`) |
| `LayoutModelInterceptor`, `LayoutModel` | `Page::build` / `LayoutModel` (`src/web/layout.rs`) |
| `GlobalExceptionHandler` | `enum AppError` + `IntoResponse` (`src/web/errors.rs`) |
| `portal:renderURL/actionURL/resourceURL` + `portal:param` | `tags.render("…").param(…)` (`src/web/tags.rs`) |
| `common:message`, `common:msgarg` | `tags.msg(…)`, `tags.msg_of(…)`, `tags.msg_with(…)` |
| `common:aclFilter`, `common:aclPageFilterTag` | `tags.can("…")`, `tags.page_allowed(…)` v `layout/base.html` |
| `form:input/select/errors` | priame HTML v šablóne + `FieldErrors::html(path)` (rovnaký `id="{path}.errors"`) |
| Bean Validation (`@NotBlank`, `@Past`, `@AssertTrue`) | `validate()` na formulároch (`src/blacklist/forms.rs`) |
| `RedisTemplate` | `redis` crate, pipeline HGET/HSET + EXPIRE, timeout 500 ms, reset spojenia po chybe |
| `RestClient` (SSO) | `reqwest` (timeouty 1 s / 2 s) |
| MockMvc + Testcontainers | `Router::oneshot` + fake `SsoApi`; Redis kontajner z `crm-web-rs/test.sh` |

## Čo Rust prinieslo

- **Chyba v šablóne pri kompilácii.** askama šablóny sú typované – preklep v poli alebo metóde je chyba buildu,
  nie runtime výnimka ako „Neither BindingResult nor plain target object…“ z logu predchádzajúceho PoC.
- **Privilégium sa nedá zabudnúť.** Handler bez `Require<…>` nedostane identitu ani session; v Jave chýbajúca anotácia
  potichu pustí každého prihláseného.
- **URL segment len ako literál.** `tags.render("detail")` berie `&'static str` – podvrhnutý segment sa nedá ani
  skompilovať (Java tag to kontroluje regexom za behu).
- **Žiadny `null`.** Chýbajúci operátor, záznam alebo dátum je `Option` – kompilátor vynúti ošetrenie.
- **Malý a rýchly runtime** (čísla nižšie).

## Čo to stálo

- Prvý build v Dockeri trvá niekoľko minút (kompilácia závislostí); ďalšie idú z cache vrstvy.
- Spring form tagy treba nahradiť ručným HTML a helperom `FieldErrors`; Bean Validation ručnou `validate()`.
- askama má vlastné pravidlá (polia sa metódam odovzdávajú referenciou, escapuje číselnými entitami `&#60;`).
- Lokálna session v pamäti procesu – rovnaké obmedzenie ako HttpSession bez replikácie v Jave.

## Čísla (namerané 2026-10-14, Docker Desktop, Windows 11)

| | Java `crm-web` | Rust `crm-web-rs` |
|---|---|---|
| produkčný kód | 2 213 riadkov Java + 382 JSP | 2 528 riadkov Rust (bez unit testov) + 304 šablóny |
| testy | 1 883 riadkov | 1 430 riadkov (54 unit + 41 integračných testov) |
| Docker image | 370 MB (`eclipse-temurin:21-jre` + WAR) | 32,3 MB (`distroless/cc-debian12` + binárka; s `debian:bookworm-slim` 83,5 MB) |
| RAM v pokoji | 283–434 MiB | 1,8–4,5 MiB |
| reštart kontajnera → prvá odpoveď | ~10,3 s | ~1,5 s (väčšina je réžia `docker compose restart`) |

Rust kód má viac riadkov hlavne pre komentáre k migračným rozhodnutiam a ručné formuláre/validáciu, ktoré v Jave
robí Spring. Funkčne sú appky zhodné: `diff` normalizovaného HTML stránok search/detail/create ukazuje len názov
appky, ladiaci riadok, poradie atribútov a spôsob escapovania `&`.

Výkon pod záťažou (rps, latencie, CPU, pamäť) a dve chyby, ktoré odhalil až záťažový test:
[`BENCHMARK.md`](BENCHMARK.md), skript `bash docker/bench.sh`.

Docker image: binárka má 8,6 MB a z OS potrebuje len glibc a libgcc_s, preto beží na `gcr.io/distroless/cc-debian12`
(24 MB, bez shellu a balíčkovača). Pôvodný `debian:bookworm-slim` tvoril 75 z 84 MB. Ešte menší (~9 MB) by bol
`FROM scratch` so statickou musl binárkou – za cenu iného alokátora a cross-kompilácie. Ladenie v kontajneri:
`docker compose exec` nemá shell; dočasne prepnúť base na `:debug-nonroot` (busybox).

## Poučenie

Kontrakt pre ďalšiu appku v ľubovoľnom jazyku je v [`../poznamky/11-appka-v-inom-jazyku.md`](../poznamky/11-appka-v-inom-jazyku.md).
Najdôležitejšie: neznáme JSON polia ignorovať, hlášky zapisovať ako celý zoznam a mazať po zobrazení, vlastná session
cookie s cestou na kontext, krátke timeouty a znovupripojenie po výpadku Redisu.
