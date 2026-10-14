# Výkon a zátěž: Java `crm-web` vs. Rust `crm-web-rs`

Tentýž blacklist, tentýž SSO a Redis kontrakt – liší se jen jazyk a runtime. Měření ukazuje, kolik stojí
JVM + Spring + JSP oproti nativní binárce s axum + askama, a odhalilo dvě chyby v Rust aplikaci (viz [níže](#co-měření-odhalilo)).

> Čísla platí pro toto demo na jednom notebooku. Nejde o produkční čísla – in-memory služby místo backendu,
> SSO mock a Redis běží na témže stroji. Užitečné jsou **poměry** a **chování pod zátěží**, ne absolutní rps.

## Jak se měří

```bash
cd docker
docker compose up -d redis sso-mock crm-web crm-web-rs   # Liferay ani nginx nejsou potřeba
bash bench.sh            # nebo: bash bench.sh 100 30s, SCENARIOS="search post" bash bench.sh
```

- Zátěž generuje [`oha`](https://github.com/hatoo/oha) z kontejneru v síti compose přímo na aplikace (`crm-web:8080`,
  `crm-web-rs:8080`) – nginx by přidal vlastní režii a změnil chování spojení (viz chyba 1).
- Skript se přihlásí do SSO mocku (operátor `spravca`, zákazník `C-1001`) a založí si platnou lokální session v obou
  aplikacích, takže požadavky jdou jako od jednoho přihlášeného uživatele.
- Každý scénář: 10 s zahřátí (JIT v Javě), poté 20 s měření, 50 souběžných keep-alive spojení.
  `docker stats` se čte v polovině měření.
- Aplikace a scénáře se spouštějí postupně, nikdy současně. Kontejnery nemají limity CPU ani paměti.

| scénář | request | co aplikace dělá |
|---|---|---|
| `css` | `GET /css/crm.css` | statický soubor, bez SSO |
| `search` | `GET /blacklist/search` | SSO identita (HTTP volání mocku), Redis (hlášky, poslední záznam), šablona celé stránky |
| `ajax` | `GET /blacklist/resource/refreshCreateConditionTypes?stateReason=DEBT` | SSO, ACL, HTML fragment |
| `post` | `POST /blacklist/searchAction` | SSO, vazba formuláře, validace, lokální session, zápis do Redisu, 302 (PRG) |
| `churn` | `POST /blacklist/searchAction` **bez session cookie**, 4 × 10 s | jako `post`, ale každý request založí novou lokální session (boti, health-checky) |

## Výsledky (2026-10-14)

Prostředí: Windows 11, Docker Desktop (WSL2, 16 CPU, 9,7 GB RAM), čerstvě restartované aplikace,
`crm-web` na `eclipse-temurin:21-jre` bez `-Xmx` (výchozí heap = ¼ RAM), `crm-web-rs` release build.

| scénář | Java rps | Rust rps | Rust/Java | Java p50 / p99 | Rust p50 / p99 |
|---|---:|---:|---:|---|---|
| `css` | **8 006** | 4 010 | 0,5× | 5,7 / 22 ms | 11,5 / 32 ms |
| `search` | 4 970 | **13 092** | 2,6× | 7,2 / 47 ms | 3,5 / 8,8 ms |
| `ajax` | 5 144 | **19 320** | 3,8× | 6,9 / 48 ms | 2,2 / 7,4 ms |
| `post` | 3 255 | **15 487** | 4,8× | 12,4 / 62 ms | 2,8 / 8,5 ms |

Zdroje během zátěže:

| | Java `crm-web` | Rust `crm-web-rs` |
|---|---|---|
| CPU (100 % = 1 jádro) | 650 % (`css`), ~1 200 % (ostatní) | 240 % (`css`), ~500 % (ostatní) |
| RAM pod zátěží | 0,7–1,1 GiB | 21–26 MiB |
| CPU na jeden request (`search`) | ~2,4 ms | ~0,4 ms |

Scénář `churn` – každé kolo přidá další session (Java i Rust je drží 30 min jako HttpSession):

| kolo (10 s) | Java rps | Rust rps | Java RAM po kole | Rust RAM po kole |
|---|---:|---:|---:|---:|
| 1 | 4 917 | 16 038 | 1,0 GiB | 98 MiB |
| 2 | 3 585 | 16 539 | 1,0 GiB | 181 MiB |
| 3 | 1 763 | 15 248 | 1,0 GiB | 277 MiB |
| 4 | 1 698 | 15 309 | 1,0 GiB | 329 MiB |

Statické údaje (image, RAM v klidu, start) jsou v [`RUST.md`](RUST.md#čísla-naměřeno-2026-10-14-docker-desktop-windows-11).

## Co z toho vyplývá

- **Logika + šablony: Rust 2,6–4,8× více requestů při ~40 % CPU.** Na request spotřebuje asi šestinu času CPU.
  Největší rozdíl je u `post` (vazba formuláře, validace, session) – Spring MVC tam má nejvíce vrstev.
- **Chvost latence:** Java má p99 5–8× vyšší než p50 (GC, rozptyl vláken Tomcatu), Rust ~2,5–3×. Pro uživatele
  je stabilní odezva často důležitější než průměr.
- **Paměť: ~40× méně.** Java heap pod zátěží roste, dokud ho nic neomezí – v produkci je třeba `-Xmx`
  a kontejnerový limit. Rust drží jen to, co skutečně potřebuje.
- **Statické soubory vyhrává Java.** Tomcat je drží v paměti, `tower-http::ServeDir` je při každém requestu čte
  z disku. V reálném nasazení je obsluhuje nginx nebo CDN, takže na tom nezáleží.
- **Strop je jinde:** u Rustu běží SSO mock (Java) na 270–350 % CPU – každý request se ptá na identitu.
  Rychlejší aplikace posouvá úzké hrdlo na závislosti; v reálném systému by se identita cachovala (stejně v obou aplikacích).
- **Churn:** Java při 100+ tis. session klesne na ~⅓ (GC nad plným heapem), Rust drží výkon a paměť roste lineárně
  (~0,5 kB na session). Obě aplikace zde chrání až expirace session – proti botům je třeba limit na úrovni nginx.

## Co měření odhalilo

Ani jednu chybu nezachytily testy ani smoke testy – projeví se až pod zátěží. Obě jsou opravené.

### 1. Chybějící `TCP_NODELAY` – +40 ms na každý request

**Příznak:** statické CSS v Rustu 252 rps při p50 50 ms, Java 3 900 rps. Při jednom spojení s keep-alive ~50 ms,
bez keep-alive 0,9 ms.

**Příčina:** axum 0.8 (na rozdíl od 0.7 a Tomcatu) nenastavuje na přijatých spojeních `TCP_NODELAY`. Odpověď jde
ve dvou zápisech (hlavičky + tělo), Nagleův algoritmus druhý zadrží, dokud nepřijde ACK, a klient s delayed ACK
čeká ~40 ms. Za nginx se to v demo stacku neprojeví, protože nginx k upstreamu ve výchozím stavu otevírá nové spojení
(HTTP/1.0) – stačí však zapnout `keepalive` v `upstream` a aplikace zpomalí 50×.

**Oprava:** [`crm-web-rs/src/main.rs`](../crm-web-rs/src/main.rs) – `listener.tap_io(|tcp| tcp.set_nodelay(true))`.

### 2. Úklid lokálních session při každém zápisu – O(n) pod zámkem

**Příznak:** `post` v Rustu 500–900 rps při CPU jen ~120 % a výkon s každým během klesal. Stejný request bez zápisu
do session (`GET /searchresult`) dával 12 000 rps.

**Příčina:** `LocalSessions::put` při každém zápisu procházel celou mapu session (`retain` expirovaných) pod jedním
globálním mutexem. Dokud je session málo, nevadí to; když klienti bez cookie zakládají nové session, je každý další request
pomalejší a všechny čekají na zámek. Po ~44 tis. session klesl Rust ve scénáři `churn` na 591 rps (Java 1 477).

**Oprava:** [`crm-web-rs/src/session/local.rs`](../crm-web-rs/src/session/local.rs) – uklízí se nejvýše jednou za
minutu (jako Tomcat `backgroundProcessorDelay`); expirovanou session `get` i `put` ignorují hned, nečekají na úklid.

## Poučení pro migraci

- Zátěžový test patří do akceptace každé nové aplikace, nejen funkční testy – obě chyby by prošly do produkce.
- Při přechodu na jiný framework/runtime zkontrolovat výchozí chování, které starý stack dělal „sám“
  (`TCP_NODELAY`, cache statických souborů, úklid session, limity heapu).
- Měřit aplikaci přímo i přes proxy – proxy může chybu skrýt (chyba 1) nebo přidat vlastní.
