# Liferay → Spring Boot: učebné demo migrácie

Ukážka, o čo ide pri migrácii legacy CRM pre zákaznícku podporu z **Liferay 6.2** (Spring Portlet MVC + JSP)
na **Spring Boot**. Podľa podkladov migrácie sa Liferay odstraňuje, ale JSP, tagy a stavová session zostávajú.
Zrozumiteľnosť a komentované rozhodnutia majú prednosť pred úplnosťou.

## Stav

| Fáza | Výstup | Stav |
|---|---|---|
| 1. Analýza legacy projektu | [`docs/ANALYZA.md`](docs/ANALYZA.md) | hotovo |
| Znalostná báza pre reálny projekt | [`poznamky/`](poznamky/README.md) | hotovo, priebežne sa dopĺňa |
| 2. Návrh dema | [spec](docs/superpowers/specs/2026-10-08-liferay-migracia-demo-design.md) | schválený |
| 2. Implementačný plán | [plán](docs/superpowers/plans/2026-10-08-liferay-migracia-demo.md) | napísaný, čaká na vykonanie |
| 2. Demo projekt | `pom.xml`, `src/`, `docs/MIGRACIA.md` | zatiaľ neexistuje |

## Čo demo ukáže

Dva zmigrované portlety:
- **Blacklist ne-zákazníkov**: render/action/resource fázy → GET / POST + redirect / AJAX, validácia, ACL.
- **Zrušenie služby**: dashboard dispatch podľa parametra → normálne URL, kontext zákazníka, JSON endpoint.

Pri oboch: „pred → po“ ukážky a mapovacia tabuľka Liferay koncept → Spring Boot náhrada.

Stack: Java 21, Spring Boot 4.1.1, Maven, WAR + JSP, in-memory služby namiesto backendu.

## Štruktúra

```
docs/        analýza, (neskôr) MIGRACIA.md, superpowers spec + plán
poznamky/    prenositeľná znalostná báza: kde čo hľadať, mapa konceptov, playbook, pasce
input/       klientske podklady – git-ignorované, read-only, nie sú súčasťou repa
```

## Anonymizácia

Repo neobsahuje klientsky kód, názvy klienta, interné URL, heslá ani dáta z logov. Ukážky „pred“ sú napísané nanovo.
Mapovanie aliasov na skutočné názvy je v lokálnych súboroch `poznamky/LOCAL-*`; tie sú git-ignorované a prenášajú sa ručne.
