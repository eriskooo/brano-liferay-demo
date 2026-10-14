# Rust kopie nové aplikace – srovnání s Java verzí

Podprojekt 3 dema: tentýž zmigrovaný blacklist jako `crm-web/` (Spring Boot 4.1.1 + JSP), napsaný v Rustu
(`crm-web-rs/`, axum 0.8 + askama 0.16). Specifikace a plán: `docs/superpowers/*/2026-10-14-crm-web-rs*`.

## Proč

Ověřit, že strangler kontrakt **nezávisí na jazyce**. Třetí aplikace v jiném jazyce se připojí k Liferay a Java aplikaci
bez jediné změny v nich – stačí dodržet cookie `SSO_ID`, SSO API a JSON v Redis hashi `crm:session:{SSO_ID}`.

## Jak to běží

```
prohlížeč  → nginx :8000 ─┬─ /web/…    → Liferay 6.2 (legacy, dashboard)
                          ├─ /crm/…    → crm-web (Java, blacklist)
                          ├─ /crm-rs/… → crm-web-rs (Rust, tentýž blacklist)   ← nové
                          └─ /sso/…    → SSO mock
všechny tři aplikace ─→ SSO API (identita z cookie SSO_ID)
všechny tři aplikace ─→ Redis hash crm:session:{SSO_ID} (hlášky, poslední kritéria, poslední záznam)
```

- Odkaz „Blacklist“ v Liferay vede dál na Java aplikaci; Rust aplikace je dostupná na vlastní cestě (a přímo na :8082).
- Vlastní session cookie `CRM_RS_SESSION` s `Path=/crm-rs` – nekoliduje s `CRM_SESSION` (Java) ani `JSESSIONID` (Liferay).
- Texty (`messages.properties`) i CSS/JS se berou **přímo z crm-web** – jeden zdroj. Rust přepíše jen rozdíly
  (`messages-rs.properties`: název aplikace, ladicí řádek, `validation.invalid`). Docker build proto má jako kontext
  kořen repozitáře a allowlist v `crm-web-rs/Dockerfile.dockerignore` (kontext ~200 kB, `input/` se do něj nedostane).
- Každá aplikace má vlastní in-memory data blacklistu (stejný seed); sdílí se jen identita a session.

## Mapování Java → Rust

| Java (Spring) | Rust (axum + askama) |
|---|---|
| `@Controller` + `@GetMapping`/`@PostMapping` | handler funkce + `Router::route` (`src/blacklist/*.rs`) |
| `@LegacyPortlet` + `LegacyPortletInterceptor` (SSO → login/401) | extraktor `Ctx` (`src/web/portlet.rs`) |
| `@RequiresPrivilege` | typový extraktor `Require<View>` / `Require<Modify>` v signatuře handleru |
| `CrmSessionArgumentResolver`, `CrmSession` | `ctx.session` (`src/session/shared.rs`) |
| `@SessionAttributes`, `HttpSession` | `LocalSessions` (`src/session/local.rs`) |
| `LayoutModelInterceptor`, `LayoutModel` | `Page::build` / `LayoutModel` (`src/web/layout.rs`) |
| `GlobalExceptionHandler` | `enum AppError` + `IntoResponse` (`src/web/errors.rs`) |
| `portal:renderURL/actionURL/resourceURL` + `portal:param` | `tags.render("…").param(…)` (`src/web/tags.rs`) |
| `common:message`, `common:msgarg` | `tags.msg(…)`, `tags.msg_of(…)`, `tags.msg_with(…)` |
| `common:aclFilter`, `common:aclPageFilterTag` | `tags.can("…")`, `tags.page_allowed(…)` v `layout/base.html` |
| `form:input/select/errors` | přímé HTML v šabloně + `FieldErrors::html(path)` (stejné `id="{path}.errors"`) |
| Bean Validation (`@NotBlank`, `@Past`, `@AssertTrue`) | `validate()` na formulářích (`src/blacklist/forms.rs`) |
| `RedisTemplate` | `redis` crate, pipeline HGET/HSET + EXPIRE, timeout 500 ms, reset spojení po chybě |
| `RestClient` (SSO) | `reqwest` (timeouty 1 s / 2 s) |
| MockMvc + Testcontainers | `Router::oneshot` + fake `SsoApi`; Redis kontejner z `crm-web-rs/test.sh` |

## Co Rust přinesl

- **Chyba v šabloně při kompilaci.** askama šablony jsou typované – překlep v poli nebo metodě je chyba buildu,
  ne runtime výjimka jako „Neither BindingResult nor plain target object…“ z logu předchozího PoC.
- **Oprávnění nelze zapomenout.** Handler bez `Require<…>` nedostane identitu ani session; v Javě chybějící anotace
  potichu pustí každého přihlášeného.
- **URL segment jen jako literál.** `tags.render("detail")` přijímá `&'static str` – podvržený segment nelze ani
  zkompilovat (Java tag to kontroluje regulárním výrazem za běhu).
- **Žádný `null`.** Chybějící operátor, záznam nebo datum je `Option` – kompilátor vynutí ošetření.
- **Malý a rychlý runtime** (čísla níže).

## Co to stálo

- První build v Dockeru trvá několik minut (kompilace závislostí); další jdou z cache vrstvy.
- Spring form tagy je třeba nahradit ručním HTML a helperem `FieldErrors`; Bean Validation ruční `validate()`.
- askama má vlastní pravidla (pole se metodám předávají referencí, escapuje číselnými entitami `&#60;`).
- Lokální session v paměti procesu – stejné omezení jako HttpSession bez replikace v Javě.

## Čísla (naměřeno 2026-10-14, Docker Desktop, Windows 11)

| | Java `crm-web` | Rust `crm-web-rs` |
|---|---|---|
| produkční kód | 2 213 řádků Java + 382 JSP | 2 528 řádků Rust (bez unit testů) + 304 šablony |
| testy | 1 883 řádků | 1 430 řádků (54 unit + 41 integračních testů) |
| Docker image | 370 MB (`eclipse-temurin:21-jre` + WAR) | 32,3 MB (`distroless/cc-debian12` + binárka; s `debian:bookworm-slim` 83,5 MB) |
| RAM v klidu | 283–434 MiB | 1,8–4,5 MiB |
| restart kontejneru → první odpověď | ~10,3 s | ~1,5 s (většinu tvoří režie `docker compose restart`) |

Rust kód má více řádků hlavně kvůli komentářům k migračním rozhodnutím a ručním formulářům/validaci, které v Javě
dělá Spring. Funkčně jsou aplikace shodné: `diff` normalizovaného HTML stránek search/detail/create ukazuje jen název
aplikace, ladicí řádek, pořadí atributů a způsob escapování `&`.

Výkon pod zátěží (rps, latence, CPU, paměť) a dvě chyby, které odhalil až zátěžový test:
[`BENCHMARK.md`](BENCHMARK.md), skript `bash docker/bench.sh`.

Docker image: binárka má 8,6 MB a z OS potřebuje jen glibc a libgcc_s, proto běží na `gcr.io/distroless/cc-debian12`
(24 MB, bez shellu a správce balíčků). Původní `debian:bookworm-slim` tvořil 75 z 84 MB. Ještě menší (~9 MB) by byl
`FROM scratch` se statickou musl binárkou – za cenu jiného alokátoru a cross-kompilace. Ladění v kontejneru:
`docker compose exec` nemá shell; dočasně přepnout base na `:debug-nonroot` (busybox).

## Poučení

Kontrakt pro další aplikaci v libovolném jazyce je v [`../poznamky/11-appka-v-inom-jazyku.md`](../poznamky/11-appka-v-inom-jazyku.md).
Nejdůležitější: neznámá JSON pole ignorovat, hlášky zapisovat jako celý seznam a mazat po zobrazení, vlastní session
cookie s cestou na kontext, krátké timeouty a opětovné připojení po výpadku Redisu.
