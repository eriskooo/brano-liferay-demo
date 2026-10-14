# Rust kópia novej appky (crm-web-rs)

Ten istý zmigrovaný **blacklist** ako `../crm-web` (Spring Boot), napísaný v Ruste (axum + askama). Beží
**súčasne** s Liferay aj Java appkou za jedným nginx pod `/crm-rs/` a zdieľa s nimi identitu (SSO mock, cookie
`SSO_ID`) aj session (Redis hash `crm:session:{SSO_ID}`). Java appka ostáva bez zmeny; odkaz „Blacklist“ v Liferay
vedie ďalej na ňu.

## Spustenie

Rust netreba inštalovať – build aj testy bežia v Dockeri.

```bash
bash crm-web-rs/test.sh            # cargo test v kontajneri + dočasný Redis (prvý beh niekoľko minút)
bash docker/build-and-deploy.sh    # celý stack vrátane crm-web-rs
bash docker/smoke-rs.sh            # na konci "RS SMOKE OK"
```

| Adresa | Čo |
|---|---|
| http://localhost:8000/crm-rs/blacklist/search | blacklist v Rust appke (cez nginx) |
| http://localhost:8000/crm/blacklist/search | ten istý blacklist v Java appke |
| http://localhost:8082/crm-rs/ | Rust appka priamo (bez CSS z Liferay) |

## Preklikanie

1. http://localhost:8000/crm-rs/blacklist/search → SSO mock → *Správca*, *C-1001* → **Prihlásiť**.
2. Hlavička ukazuje *CRM – nová appka (Rust)* a ladiaci riadok *zdroj čítania = Redis (Rust appka)*.
3. Vyhľadaj `vzork`, otvor **Peter Vzorka**. V druhom tabe otvor Java appku `/crm/blacklist/search` – jej hlavička
   ukáže *Naposledy otvorený záznam: Peter Vzorka* (zapísala ho Rust appka).
4. V Rust appke zmeň stav záznamu a hneď otvor Java appku – hláška *Záznam bol zmenený.* sa zobrazí tam, a len raz.
5. Pri flagu `REDIS` (`bash docker/session-flag.sh REDIS`) to isté vidí aj dashboard v Liferay.

## Čo je kde

| Koncept (Java) | Rust |
|---|---|
| controllery blacklistu | `src/blacklist/search.rs`, `detail.rs`, `create.rs` |
| `LegacyPortletInterceptor` + `@RequiresPrivilege` | `src/web/portlet.rs` (`Ctx`, `Require<View>`, `Require<Modify>`) |
| JSP tagy `portal:*` / `common:*` | `src/web/tags.rs` (`Tags`, `FieldErrors`) |
| JSP stránky | `templates/` (askama) |
| `CrmSession`, `RedisSharedSessionStore`, `SharedSessionKeys` | `src/session/shared.rs`, `store.rs`, `keys.rs` |
| `HttpSession` (`CRM_SESSION`) | `src/session/local.rs` (`CRM_RS_SESSION`, Path=/crm-rs) |
| `SsoClient`, `SsoIdentityResolver` | `src/session/sso_client.rs`, `identity.rs` |
| `messages.properties` | číta sa z `crm-web` pri kompilácii, rozdiely v `messages-rs.properties` |

Porovnanie Java vs Rust: [`../docs/RUST.md`](../docs/RUST.md).

## Vedomé obmedzenia

- Vlastné in-memory dáta blacklistu (rovnaký seed) – zmena v Rust appke sa v Java appke neprejaví; zdieľa sa len session a identita.
- Lokálna session (kritériá vyhľadávania) je v pamäti procesu – reštart kontajnera ju zmaže (ako HttpSession bez replikácie).
- Neplatný dátum/enum vo formulári ukáže „Neplatná hodnota.“ (Java ukáže technickú hlášku Springu).
