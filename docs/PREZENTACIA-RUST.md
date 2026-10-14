---
marp: true
theme: default
paginate: true
title: Migrace CRM z Javy do Rustu
---

<!-- Slajdy ve formátu Marp (VS Code „Marp for VS Code“ → Export PDF/PPTX); čitelné i jako obyčejný Markdown. -->

# Migrace CRM z Javy do Rustu

### Výsledky pilotního srovnání

Modul blacklist implementovaný v Javě (Spring Boot) a v Rustu (axum).
Stejná funkcionalita, stejné rozhraní, stejné závislosti.

---

# Shrnutí

1. **Výkon:** Rust zpracuje 2,6–4,8× více požadavků při méně než poloviční spotřebě CPU.
2. **Provoz:** Rust potřebuje přibližně 40× méně paměti; Docker image je 11× menší.
3. **Migrace:** obě aplikace mohou běžet souběžně; přechod probíhá po jednotlivých stránkách a je kdykoli vratný.

---

# Rozsah a podmínky měření

| | Java `crm-web` | Rust `crm-web-rs` |
|---|---|---|
| Funkcionalita | vyhledávání, detail, změna záznamu, AJAX | shodná |
| Uživatelské rozhraní | HTML a CSS | shodné |
| Autentizace | SSO | shodná |
| Sdílená relace | Redis | shodná |

- 50 souběžných spojení, 20 s měření po 10 s zahřátí, aplikace měřeny postupně.
- Jeden stroj (16 CPU), Docker, backend nahrazený daty v paměti.
- Absolutní hodnoty nejsou produkční; vypovídající jsou poměry.

---

# Propustnost a latence

| Operace | Java | Rust | Poměr |
|---|---:|---:|---:|
| Zobrazení stránky | 4 970 req/s | 13 092 req/s | 2,6× |
| AJAX požadavek | 5 144 req/s | 19 320 req/s | 3,8× |
| Odeslání formuláře | 3 255 req/s | 15 487 req/s | 4,8× |
| Latence p99 | 47–62 ms | 7–9 ms | 5–7× nižší |
| Využití CPU | ~12 jader | ~5 jader | |

Výjimka: statické soubory obsluhuje Java 2× rychleji. V produkci je obsluhuje nginx, výsledek to neovlivňuje.

---

# Nároky na provoz

| | Java | Rust |
|---|---:|---:|
| Paměť pod zátěží | 0,7–1,1 GB | 21–26 MB |
| Velikost Docker image | 370 MB | 32 MB |
| Doba startu | ~10 s | ~1 s |
| Výkon při 100 000+ relacích | pokles na ~⅓ | bez poklesu |

Důsledky: vyšší hustota instancí na server, rychlejší škálování a nasazení,
menší rozsah runtime prostředí (image bez shellu a správce balíčků).

---

# Postup migrace

Postupná náhrada (vzor *strangler*):

1. Rust aplikace běží souběžně s Java aplikací.
2. Obě používají společné SSO a společnou relaci v Redisu.
3. nginx směruje vybrané stránky do Rust aplikace; návrat je změna směrování.
4. Moduly se přesouvají postupně; Java aplikace se odstaví po přesunu posledního modulu.

Vývoj stávající aplikace během migrace pokračuje.

---

# Náklady a rizika

| Oblast | Dopad |
|---|---|
| Implementace | kód se přepisuje; rozsah srovnatelný s původním (~2 600 → ~2 800 řádků) |
| Kompetence týmu | nutné zaškolení v Rustu |
| Ekosystém | méně hotových komponent než Spring (formuláře, validace) |
| Kvalita | zátěžové testy jsou nezbytnou součástí každého kroku |

Zátěžové testy v pilotu odhalily dvě výkonnostní chyby, které funkční testy nezachytily. Obě jsou opravené.

---

# Otevřené otázky

Zkušenosti týmu s Rustem jsou zatím omezené. Následující oblasti vyžadují další ověření:

- **Menší image (7 MB):** statická binárka (musl) se standardním alokátorem snížila výkon o 30–55 %;
  s alokátorem mimalloc je výkon zachován, ale spotřeba paměti vzrostla ~4×. Zůstáváme u 32MB varianty.
- **Závislosti:** při vyšším výkonu se úzkým místem stává SSO služba; řešením je cache identity.
- **Výchozí nastavení:** část chování, které Spring a Tomcat zajišťují automaticky, je třeba v Rustu
  nastavit explicitně (síťové parametry, správa relací).

---

# Doporučení

### Pilotní migrace jednoho modulu

1. Výběr modulu s vysokou zátěží.
2. Implementace v Rustu a souběžný provoz s Javou.
3. Měření na reálných datech a v reálném provozu.
4. Rozhodnutí o dalším postupu na základě naměřených výsledků.

Pilot je vratný bez zásahu do Java aplikace.

---

# Podklady

- [`docs/BENCHMARK.md`](BENCHMARK.md) – metodika a kompletní výsledky měření
- [`docs/RUST.md`](RUST.md) – architektura Rust aplikace
- `bash docker/bench.sh` – reprodukce měření
