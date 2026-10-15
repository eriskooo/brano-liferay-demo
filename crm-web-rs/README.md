# Rust kopie nové aplikace (crm-web-rs)

Tentýž zmigrovaný **blacklist** jako `../crm-web` (Spring Boot), napsaný v Rustu (axum + askama). Běží
**souběžně** s Liferay i Java aplikací za jedním nginx pod `/crm-rs/` a sdílí s nimi identitu (SSO mock, cookie
`SSO_ID`) i session (Redis hash `crm:session:{SSO_ID}`). Java aplikace zůstává beze změny; odkaz „Blacklist“ v Liferay
vede dál na ni.

## Spuštění

Rust není třeba instalovat – build i testy běží v Dockeru.

```bash
bash crm-web-rs/test.sh                     # cargo test v kontejneru + dočasný Redis (první běh několik minut)
bash docker/build-and-deploy.sh --with-rs   # celý stack včetně crm-web-rs (bez přepínače se nespouští)
bash docker/smoke-rs.sh                     # na konci "RS SMOKE OK"
```

| Adresa | Co |
|---|---|
| http://localhost:8000/crm-rs/blacklist/search | blacklist v Rust aplikaci (přes nginx) |
| http://localhost:8000/crm/blacklist/search | tentýž blacklist v Java aplikaci |
| http://localhost:8082/crm-rs/ | Rust aplikace přímo (bez CSS z Liferay) |

## Proklikání

1. http://localhost:8000/crm-rs/blacklist/search → SSO mock → *Správca*, *C-1001* → **Prihlásiť**.
2. Hlavička ukazuje *CRM – nová appka (Rust)* a ladicí řádek *zdroj čítania = Redis (Rust appka)*.
3. Vyhledej `vzork`, otevři **Peter Vzorka**. Ve druhém tabu otevři Java aplikaci `/crm/blacklist/search` – její hlavička
   ukáže *Naposledy otvorený záznam: Peter Vzorka* (zapsala ho Rust aplikace).
4. V Rust aplikaci změň stav záznamu a hned otevři Java aplikaci – hláška *Záznam bol zmenený.* se zobrazí tam, a jen jednou.
5. Při flagu `REDIS` (`bash docker/session-flag.sh REDIS`) totéž vidí i dashboard v Liferay.

## Co je kde

| Koncept (Java) | Rust |
|---|---|
| controllery blacklistu | `src/blacklist/search.rs`, `detail.rs`, `create.rs` |
| `LegacyPortletInterceptor` + `@RequiresPrivilege` | `src/web/portlet.rs` (`Ctx`, `Require<View>`, `Require<Modify>`) |
| JSP tagy `portal:*` / `common:*` | `src/web/tags.rs` (`Tags`, `FieldErrors`) |
| JSP stránky | `templates/` (askama) |
| `CrmSession`, `RedisSharedSessionStore`, `SharedSessionKeys` | `src/session/shared.rs`, `store.rs`, `keys.rs` |
| `HttpSession` (`CRM_SESSION`) | `src/session/local.rs` (`CRM_RS_SESSION`, Path=/crm-rs) |
| `SsoClient`, `SsoIdentityResolver` | `src/session/sso_client.rs`, `identity.rs` |
| `messages.properties` | čte se z `crm-web` při kompilaci, rozdíly v `messages-rs.properties` |

Srovnání Java vs Rust: [`../docs/RUST.md`](../docs/RUST.md).

## Vědomá omezení

- Vlastní in-memory data blacklistu (stejný seed) – změna v Rust aplikaci se v Java aplikaci neprojeví; sdílí se jen session a identita.
- Lokální session (kritéria vyhledávání) je v paměti procesu – restart kontejneru ji smaže (jako HttpSession bez replikace).
- Neplatné datum/enum ve formuláři ukáže „Neplatná hodnota.“ (Java ukáže technickou hlášku Springu).
