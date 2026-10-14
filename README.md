# Liferay → Spring Boot: učebné demo migrácie

![Súbeh starej a novej appky za nginx so zdieľanou session v Redise](docs/img/architektura-subeh.svg)

Ukážka, o čo ide pri migrácii legacy CRM pre zákaznícku podporu z **Liferay 6.2** (Spring Portlet MVC + JSP)
na **Spring Boot**. Podľa podkladov migrácie sa Liferay odstraňuje, ale JSP, tagy a stavová session zostávajú.
Zrozumiteľnosť a komentované rozhodnutia majú prednosť pred úplnosťou.

## Architektúra počas migrácie

- **nginx** je jediný vstup (`http://localhost:8000`). Zmigrované stránky smeruje do Spring Boot, ostatné do Liferay
  (strangler): blacklist je už v novej appke, dashboard ešte v Liferay.
- Identitu (operátor, zákazník) vlastní **SSO portál** – obe appky ju čítajú podľa cookie `SSO_ID`.
- **Redis** drží zdieľanú session `crm:session:{SSO_ID}`. Nová appka do nej zapisuje a číta vždy, stará zapisuje
  do PortletSession aj Redisu a číta podľa flagu – aby videla, čo zapísala nová appka, musí byť flag `REDIS`.
- Preklikanie súbehu: [`crm-web/README.md`](crm-web/README.md); obsah session naživo: `bash docker/show-session.sh`.
- **Blacklist štandardne obsluhuje Java appka `crm-web`** (`/crm/…`) – sem vedie odkaz „Blacklist“ v Liferay aj `http://localhost:8000/`.
- **Bonus – Rust kópia** [`crm-web-rs/`](crm-web-rs/README.md): ten istý blacklist v Ruste (axum + askama) na vlastnej ceste
  `http://localhost:8000/crm-rs/blacklist/search`. Na migráciu nie je potrebná; ukazuje, že identita a zdieľaná session
  fungujú aj s appkou v inom jazyku ([`docs/RUST.md`](docs/RUST.md)).
  - ⚡ **Výkon Java vs. Rust:** pri rovnakej logike Rust spracuje 2,6–4,8× viac requestov s ~40× menšou pamäťou
    a záťažový test odhalil dve chyby, ktoré funkčné testy nezachytili – **[`docs/BENCHMARK.md`](docs/BENCHMARK.md)**
    (spustenie: `bash docker/bench.sh`).
  - 🎯 **Prezentácia pre zákazníka** (Java → Rust, slajdy Marp): [`docs/PREZENTACIA-RUST.md`](docs/PREZENTACIA-RUST.md).

## Stav

| Fáza | Výstup | Stav |
|---|---|---|
| 1. Analýza legacy projektu | [`docs/ANALYZA.md`](docs/ANALYZA.md) | hotovo |
| Znalostná báza pre reálny projekt | [`poznamky/`](poznamky/README.md) | hotovo, priebežne sa dopĺňa |
| 2a. Stará appka „pred“ (Liferay 6.2) | [`legacy/`](legacy/README.md), [`sso-mock/`](sso-mock), [`docker/`](docker) – [spec](docs/superpowers/specs/2026-10-12-legacy-liferay-app-design.md), [plán](docs/superpowers/plans/2026-10-12-legacy-liferay-app.md) | hotovo |
| 2b. Zmigrovaná appka „po“ (Spring Boot) | [`crm-web/`](crm-web/README.md), [`docs/MIGRACIA.md`](docs/MIGRACIA.md) – [spec](docs/superpowers/specs/2026-10-12-crm-web-migration-design.md), [plán](docs/superpowers/plans/2026-10-12-crm-web-migration.md) | hotovo – blacklist beží v Spring Boot vedľa Liferay |
| Bonus: Rust kópia `crm-web` | [`crm-web-rs/`](crm-web-rs/README.md), [`docs/RUST.md`](docs/RUST.md), [výkon](docs/BENCHMARK.md) – [spec](docs/superpowers/specs/2026-10-14-crm-web-rs-design.md), [plán](docs/superpowers/plans/2026-10-14-crm-web-rs.md) | hotovo – voliteľná ukážka pod `/crm-rs`, Java appka ostáva predvolená |

## Čo demo ukáže

Dva zmigrované portlety:
- **Blacklist ne-zákazníkov**: render/action/resource fázy → GET / POST + redirect / AJAX, validácia, ACL.
- **Zrušenie služby**: dashboard dispatch podľa parametra → normálne URL, kontext zákazníka, JSON endpoint.

Pri oboch: „pred → po“ ukážky a mapovacia tabuľka Liferay koncept → Spring Boot náhrada.

Stack: Java 21, Spring Boot 4.1.1, Maven, WAR + JSP, in-memory služby namiesto backendu.
Bonus `crm-web-rs`: Rust 1.98, axum + askama; build aj testy v Dockeri (`bash crm-web-rs/test.sh`), lokálny Rust netreba.

## Štruktúra

```
legacy/      stará appka na Liferay 6.2 (Java 8, Spring Portlet MVC) – 4 moduly ako legacy
crm-web/     nová appka (Spring Boot 4.1.1) so zmigrovaným blacklistom – beží súčasne so starou
crm-web-rs/  bonus: Rust kópia crm-web pod /crm-rs (voliteľná, nie je súčasťou migrácie)
sso-mock/    mock SSO portálu (Spring Boot 4.1.1)
docker/      Liferay 6.2 + Redis + RedisInsight + SSO mock + nginx, skripty (build, smoke, zdieľaná session)
docs/        analýza, (neskôr) MIGRACIA.md, superpowers spec + plány
poznamky/    prenositeľná znalostná báza: kde čo hľadať, mapa konceptov, playbook, pasce
input/       klientske podklady – git-ignorované, read-only, nie sú súčasťou repa
```

## Anonymizácia

Repo neobsahuje klientsky kód, názvy klienta, interné URL, heslá ani dáta z logov. Ukážky „pred“ sú napísané nanovo.
Mapovanie aliasov na skutočné názvy je v lokálnych súboroch `poznamky/LOCAL-*`; tie sú git-ignorované a prenášajú sa ručne.
