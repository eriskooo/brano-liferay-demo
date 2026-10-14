---
marp: true
theme: default
paginate: true
title: Migrácia CRM z Javy do Rustu
---

<!-- Slajdy vo formáte Marp (VS Code „Marp for VS Code“ → Export PDF/PPTX); čitateľné aj ako obyčajný Markdown. -->

# Migrácia CRM z Javy do Rustu

### Výsledky pilotného porovnania

Modul blacklist implementovaný v Jave (Spring Boot) a v Ruste (axum).
Rovnaká funkcionalita, rovnaké rozhranie, rovnaké závislosti.

---

# Zhrnutie

1. **Výkon:** Rust spracuje 2,6–4,8× viac požiadaviek pri menej ako polovičnej spotrebe CPU.
2. **Prevádzka:** Rust potrebuje približne 40× menej pamäte; Docker image je 11× menší.
3. **Migrácia:** obe aplikácie môžu bežať súbežne; prechod prebieha po jednotlivých stránkach a je kedykoľvek vratný.

---

# Rozsah a podmienky merania

| | Java `crm-web` | Rust `crm-web-rs` |
|---|---|---|
| Funkcionalita | vyhľadávanie, detail, zmena záznamu, AJAX | zhodná |
| Používateľské rozhranie | HTML a CSS | zhodné |
| Autentifikácia | SSO | zhodná |
| Zdieľaná relácia | Redis | zhodná |

- 50 súbežných spojení, 20 s meranie po 10 s zahriatí, aplikácie merané postupne.
- Jeden stroj (16 CPU), Docker, backend nahradený dátami v pamäti.
- Absolútne hodnoty nie sú produkčné; vypovedajúce sú pomery.

---

# Priepustnosť a latencia

| Operácia | Java | Rust | Pomer |
|---|---:|---:|---:|
| Zobrazenie stránky | 4 970 req/s | 13 092 req/s | 2,6× |
| AJAX požiadavka | 5 144 req/s | 19 320 req/s | 3,8× |
| Odoslanie formulára | 3 255 req/s | 15 487 req/s | 4,8× |
| Latencia p99 | 47–62 ms | 7–9 ms | 5–7× nižšia |
| Využitie CPU | ~12 jadier | ~5 jadier | |

Výnimka: statické súbory obsluhuje Java 2× rýchlejšie. V produkcii ich obsluhuje nginx, výsledok to neovplyvňuje.

---

# Nároky na prevádzku

| | Java | Rust |
|---|---:|---:|
| Pamäť pod záťažou | 0,7–1,1 GB | 21–26 MB |
| Veľkosť Docker image | 370 MB | 32 MB |
| Čas štartu | ~10 s | ~1 s |
| Výkon pri 100 000+ reláciách | pokles na ~⅓ | bez poklesu |

Dôsledky: vyššia hustota inštancií na server, rýchlejšie škálovanie a nasadenie,
menší rozsah runtime prostredia (image bez shellu a správcu balíkov).

---

# Postup migrácie

Postupná náhrada (vzor *strangler*):

1. Rust aplikácia beží súbežne s Java aplikáciou.
2. Obe používajú spoločné SSO a spoločnú reláciu v Redise.
3. nginx smeruje vybrané stránky do Rust aplikácie; návrat je zmena smerovania.
4. Moduly sa presúvajú postupne; Java aplikácia sa odstaví po presune posledného modulu.

Vývoj existujúcej aplikácie počas migrácie pokračuje.

---

# Náklady a riziká

| Oblasť | Dopad |
|---|---|
| Implementácia | kód sa prepisuje; rozsah porovnateľný s pôvodným (~2 600 → ~2 800 riadkov) |
| Kompetencie tímu | potrebné zaškolenie v Ruste |
| Ekosystém | menej hotových komponentov ako Spring (formuláre, validácia) |
| Kvalita | záťažové testy sú nutnou súčasťou každého kroku |

Záťažové testy v pilote odhalili dve výkonové chyby, ktoré funkčné testy nezachytili. Obe sú opravené.

---

# Otvorené otázky

Skúsenosti tímu s Rustom sú zatiaľ obmedzené. Nasledujúce oblasti vyžadujú ďalšie overenie:

- **Menší image (7 MB):** statická binárka (musl) so štandardným alokátorom znížila výkon o 30–55 %;
  s alokátorom mimalloc je výkon zachovaný, ale spotreba pamäte vzrástla ~4×. Ostávame pri 32 MB variante.
- **Závislosti:** pri vyššom výkone sa úzkym miestom stáva SSO služba; riešením je cache identity.
- **Predvolené nastavenia:** časť správania, ktoré Spring a Tomcat zabezpečujú automaticky, treba v Ruste
  nastaviť explicitne (sieťové parametre, správa relácií).

---

# Odporúčanie

### Pilotná migrácia jedného modulu

1. Výber modulu s vysokou záťažou.
2. Implementácia v Ruste a súbežná prevádzka s Javou.
3. Meranie na reálnych dátach a reálnej prevádzke.
4. Rozhodnutie o ďalšom postupe na základe nameraných výsledkov.

Pilot je vratný bez zásahu do Java aplikácie.

---

# Podklady

- [`docs/BENCHMARK.md`](BENCHMARK.md) – metodika a kompletné výsledky merania
- [`docs/RUST.md`](RUST.md) – architektúra Rust aplikácie
- `bash docker/bench.sh` – reprodukcia merania
