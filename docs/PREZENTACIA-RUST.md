---
marp: true
theme: default
paginate: true
title: CRM z Javy do Rustu
---

<!-- Slajdy vo formáte Marp (VS Code „Marp for VS Code“ → Export PDF/PPTX); čitateľné aj ako obyčajný Markdown. -->

# CRM z Javy do Rustu

### Rovnaká aplikácia, zlomok zdrojov

Blacklist zo zákazníckeho CRM – raz v Jave (Spring Boot), raz v Ruste.
Rovnaké obrazovky, rovnaké prihlásenie, rovnaké dáta. Zmerali sme rozdiel.

---

# Pointa

> **Rust obslúži 3–5× viac používateľov na rovnakom serveri,
> spotrebuje ~40× menej pamäte – a dá sa nasadiť postupne, bez veľkého tresku.**

Menej serverov → nižší účet za infraštruktúru. Používateľ si zmenu nevšimne – len rýchlejšiu odozvu.

---

# Čo sme porovnávali

|  | Java `crm-web` | Rust `crm-web-rs` |
|---|---|---|
| Funkcie | blacklist: hľadanie, detail, zmena, AJAX | **to isté**, 1:1 |
| Vzhľad | rovnaké HTML a CSS | **to isté** |
| Prihlásenie | SSO | **to isté** SSO |
| Zdieľaná session | Redis | **ten istý** Redis |

Obe aplikácie bežia **súčasne** vedľa seba a navzájom vidia, čo používateľ urobil v tej druhej.

---

# Výkon pod záťažou

50 súbežných používateľov, rovnaký stroj, rovnaké závislosti.

| Akcia | Java | Rust | |
|---|---:|---:|---|
| Otvorenie stránky | 4 970 /s | **13 092 /s** | 2,6× |
| AJAX (výber v formulári) | 5 144 /s | **19 320 /s** | 3,8× |
| Odoslanie formulára | 3 255 /s | **15 487 /s** | 4,8× |
| Najpomalšia 1 % odpovedí | 47–62 ms | **8–9 ms** | 5–7× |

Rust pritom spotrebuje **menej ako polovicu CPU**.

---

# Prevádzka

| | Java | Rust |
|---|---:|---:|
| Pamäť pod záťažou | ~1 GB | **~25 MB** |
| Veľkosť Docker image | 370 MB | **32 MB** |
| Štart aplikácie | ~10 s | **~1 s** |
| Pri útoku botov (tisíce nových session) | spomalí na ⅓ | **drží výkon** |

Prakticky: viac inštancií na jeden server, rýchlejšie nasadenie a škálovanie, menšia plocha pre útok
(image bez shellu a balíčkovača).

---

# Ako migrovať bez rizika

**Postupne, stránka po stránke** (vzor *strangler*):

1. Nová Rust aplikácia beží **vedľa** existujúcej Javy.
2. Prihlásenie (SSO) a session (Redis) **zdieľajú** – používateľ nič nepostrehne.
3. nginx presmeruje vybranú stránku do Rustu. **Návrat späť = jeden riadok konfigurácie.**
4. Ďalšia stránka, ďalší modul… Java sa vypne, až keď je prázdna.

Žiadny „deň D“, žiadne zmrazenie vývoja.

---

# Čo to stojí

- **Kód sa prepisuje**, nekopíruje. Blacklist: ~2 600 riadkov Java + JSP → ~2 800 riadkov Rust + šablóny.
- **Tím sa musí naučiť Rust** – strmší začiatok, odmenou je kompilátor, ktorý chytí veľa chýb pred produkciou.
- **Menej hotových riešení** než v Spring – formuláre a validáciu sme písali ručne.
- **Záťažové testy sú povinnosť** – v našom pilote odhalili dve chyby, ktoré funkčné testy nevideli
  (obe opravené za hodinu).

---

# Kedy sa to oplatí

✅ **Áno**, ak
- platíte veľa za servery / cloud alebo máte výkyvy záťaže,
- záleží na rýchlej a stabilnej odozve,
- plánujete aplikáciu prevádzkovať ešte roky.

⚠️ **Zvážte**, ak
- aplikácia je malá a záťaž nízka – úspora nevyváži prepis,
- tím nemá kapacitu sa učiť nový jazyk.

---

# Čo ešte skúmame

S Rustom sme **začiatočníci** – a priznávame to. Pilot ukázal smer, nie strop.

- **Ešte menší image (7 MB namiesto 32 MB):** statická binárka (musl) to dokáže, ale jej alokátor pamäte
  zrazil výkon o 30–55 %. S alokátorom *mimalloc* sa výkon vrátil, no pamäť stúpla ~4× (≈ 90 MB).
  Zatiaľ ostávame pri rýchlejšom a úspornejšom riešení; ladenie alokátora je otvorená téma.
- **Statické súbory:** Java ich servíruje 2× rýchlejšie (drží ich v pamäti) – v produkcii ich aj tak dá nginx/CDN.
- **Úzke hrdlo sa presunulo:** Rust čaká na SSO službu (Java) – cache identity by pomohla obom aplikáciám.
- **Rust „nerobí veci sám“:** dve výkonové chyby (sieťové nastavenie, upratovanie session) sme našli až
  záťažovým testom. Záťažový test preto patrí do každého kroku migrácie.

---

# Návrh ďalšieho kroku

### Pilot na jednom module

1. Vyberieme jeden modul s vysokou záťažou.
2. Prepíšeme ho do Rustu, spustíme vedľa Javy.
3. Zmeriame na **vašich** dátach a **vašej** prevádzke.
4. Rozhodnete sa na základe čísel, nie sľubov.

**Riziko: minimálne** – kedykoľvek sa vrátime k Jave jednou zmenou v nginx.

---

# Ďakujeme

Podrobné meranie, postup a zdrojový kód:
- [`docs/BENCHMARK.md`](BENCHMARK.md) – metodika a všetky čísla
- [`docs/RUST.md`](RUST.md) – ako je Rust aplikácia postavená
- `bash docker/bench.sh` – meranie si zopakujete sami
