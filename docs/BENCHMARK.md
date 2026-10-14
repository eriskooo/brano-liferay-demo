# Výkon a záťaž: Java `crm-web` vs. Rust `crm-web-rs`

Ten istý blacklist, ten istý SSO a Redis kontrakt – líši sa len jazyk a runtime. Meranie ukazuje, koľko stojí
JVM + Spring + JSP oproti natívnej binárke s axum + askama, a odhalilo dve chyby v Rust appke (pozri [nižšie](#čo-meranie-odhalilo)).

> Čísla platia pre toto demo na jednom notebooku. Nie sú to produkčné čísla – in-memory služby namiesto backendu,
> SSO mock a Redis bežia na tom istom stroji. Užitočné sú **pomery** a **správanie pod záťažou**, nie absolútne rps.

## Ako sa meria

```bash
cd docker
docker compose up -d redis sso-mock crm-web crm-web-rs   # Liferay ani nginx netreba
bash bench.sh            # alebo: bash bench.sh 100 30s, SCENARIOS="search post" bash bench.sh
```

- Záťaž generuje [`oha`](https://github.com/hatoo/oha) z kontajnera v sieti compose priamo na appky (`crm-web:8080`,
  `crm-web-rs:8080`) – nginx by pridal vlastnú réžiu a zmenil správanie spojení (pozri chybu 1).
- Skript sa prihlási do SSO mocku (operátor `spravca`, zákazník `C-1001`) a založí si platnú lokálnu session v oboch
  appkách, takže requesty idú ako od jedného prihláseného používateľa.
- Každý scenár: 10 s zahriatie (JIT v Jave), potom 20 s meranie, 50 súbežných keep-alive spojení.
  `docker stats` sa číta v polovici merania.
- Appky a scenáre idú postupne, nikdy nie súčasne. Kontajnery nemajú limity CPU ani pamäte.

| scenár | request | čo appka robí |
|---|---|---|
| `css` | `GET /css/crm.css` | statický súbor, bez SSO |
| `search` | `GET /blacklist/search` | SSO identita (HTTP volanie mocku), Redis (hlášky, posledný záznam), šablóna celej stránky |
| `ajax` | `GET /blacklist/resource/refreshCreateConditionTypes?stateReason=DEBT` | SSO, ACL, HTML fragment |
| `post` | `POST /blacklist/searchAction` | SSO, väzba formulára, validácia, lokálna session, zápis do Redisu, 302 (PRG) |
| `churn` | `POST /blacklist/searchAction` **bez session cookie**, 4 × 10 s | ako `post`, ale každý request založí novú lokálnu session (boti, health-checky) |

## Výsledky (2026-10-14)

Prostredie: Windows 11, Docker Desktop (WSL2, 16 CPU, 9,7 GB RAM), čerstvo reštartované appky,
`crm-web` na `eclipse-temurin:21-jre` bez `-Xmx` (predvolený heap = ¼ RAM), `crm-web-rs` release build.

| scenár | Java rps | Rust rps | Rust/Java | Java p50 / p99 | Rust p50 / p99 |
|---|---:|---:|---:|---|---|
| `css` | **8 006** | 4 010 | 0,5× | 5,7 / 22 ms | 11,5 / 32 ms |
| `search` | 4 970 | **13 092** | 2,6× | 7,2 / 47 ms | 3,5 / 8,8 ms |
| `ajax` | 5 144 | **19 320** | 3,8× | 6,9 / 48 ms | 2,2 / 7,4 ms |
| `post` | 3 255 | **15 487** | 4,8× | 12,4 / 62 ms | 2,8 / 8,5 ms |

Zdroje počas záťaže:

| | Java `crm-web` | Rust `crm-web-rs` |
|---|---|---|
| CPU (100 % = 1 jadro) | 650 % (`css`), ~1 200 % (ostatné) | 240 % (`css`), ~500 % (ostatné) |
| RAM pod záťažou | 0,7–1,1 GiB | 21–26 MiB |
| CPU na jeden request (`search`) | ~2,4 ms | ~0,4 ms |

Scenár `churn` – každé kolo pridá ďalšie session (Java aj Rust ich držia 30 min ako HttpSession):

| kolo (10 s) | Java rps | Rust rps | Java RAM po kole | Rust RAM po kole |
|---|---:|---:|---:|---:|
| 1 | 4 917 | 16 038 | 1,0 GiB | 98 MiB |
| 2 | 3 585 | 16 539 | 1,0 GiB | 181 MiB |
| 3 | 1 763 | 15 248 | 1,0 GiB | 277 MiB |
| 4 | 1 698 | 15 309 | 1,0 GiB | 329 MiB |

Statické údaje (image, RAM v pokoji, štart) sú v [`RUST.md`](RUST.md#čísla-namerané-2026-10-14-docker-desktop-windows-11).

## Čo z toho vyplýva

- **Logika + šablóny: Rust 2,6–4,8× viac requestov pri ~40 % CPU.** Na request spotrebuje asi šestinu CPU času.
  Najväčší rozdiel je na `post` (väzba formulára, validácia, session) – Spring MVC tam má najviac vrstiev.
- **Chvost latencie:** Java má p99 5–8× vyšší ako p50 (GC, rozptyl vlákien Tomcatu), Rust ~2,5–3×. Pre používateľa
  je stabilná odozva často dôležitejšia než priemer.
- **Pamäť: ~40× menej.** Java heap pod záťažou narastie, kým ho nič neobmedzí – v produkcii treba `-Xmx`
  a kontajnerový limit. Rust drží len to, čo reálne potrebuje.
- **Statické súbory vyhráva Java.** Tomcat ich drží v pamäti, `tower-http::ServeDir` ich pri každom requeste číta
  z disku. V reálnom nasadení ich servíruje nginx alebo CDN, takže na tom nezáleží.
- **Strop je inde:** pri Ruste SSO mock (Java) beží na 270–350 % CPU – každý request sa pýta na identitu.
  Rýchlejšia appka posúva úzke hrdlo na závislosti; v reálnom systéme by sa identita cachovala (rovnako v oboch appkách).
- **Churn:** Java pri 100+ tis. session klesne na ~⅓ (GC nad plným heapom), Rust drží výkon a pamäť rastie lineárne
  (~0,5 kB na session). Obe appky tu chráni až expirácia session – proti botom treba limit na úrovni nginx.

## Čo meranie odhalilo

Obe chyby testy ani smoke testy nezachytili – prejavia sa až pod záťažou. Obe sú opravené.

### 1. Chýbajúci `TCP_NODELAY` – +40 ms na každý request

**Príznak:** statické CSS v Ruste 252 rps pri p50 50 ms, Java 3 900 rps. Pri jednom spojení s keep-alive ~50 ms,
bez keep-alive 0,9 ms.

**Príčina:** axum 0.8 (na rozdiel od 0.7 a Tomcatu) nenastavuje na prijatých spojeniach `TCP_NODELAY`. Odpoveď ide
v dvoch zápisoch (hlavičky + telo), Nagleov algoritmus druhý zadrží, kým nepríde ACK, a klient s delayed ACK
čaká ~40 ms. Za nginx sa to v demo stacku neprejaví, lebo nginx k upstreamu predvolene otvára nové spojenie
(HTTP/1.0) – stačí však zapnúť `keepalive` v `upstream` a appka spomalí 50×.

**Oprava:** [`crm-web-rs/src/main.rs`](../crm-web-rs/src/main.rs) – `listener.tap_io(|tcp| tcp.set_nodelay(true))`.

### 2. Upratovanie lokálnych session pri každom zápise – O(n) pod zámkom

**Príznak:** `post` v Ruste 500–900 rps pri CPU len ~120 % a výkon s každým behom klesal. Rovnaký request bez zápisu
do session (`GET /searchresult`) dával 12 000 rps.

**Príčina:** `LocalSessions::put` pri každom zápise prechádzal celú mapu session (`retain` expirovaných) pod jedným
globálnym mutexom. Kým je session málo, nevadí to; keď klienti bez cookie zakladajú nové session, každý ďalší request
je pomalší a všetky čakajú na zámok. Po ~44 tis. session klesol Rust v scenári `churn` na 591 rps (Java 1 477).

**Oprava:** [`crm-web-rs/src/session/local.rs`](../crm-web-rs/src/session/local.rs) – upratuje sa najviac raz za
minútu (ako Tomcat `backgroundProcessorDelay`); expirovanú session `get` aj `put` ignorujú hneď, nečakajú na upratanie.

## Poučenie pre migráciu

- Záťažový test patrí do akceptácie každej novej appky, nielen funkčné testy – obe chyby by prešli do produkcie.
- Pri prechode na iný framework/runtime skontrolovať predvolené správanie, ktoré starý stack robil „sám“
  (`TCP_NODELAY`, cache statických súborov, upratovanie session, limity heapu).
- Merať appku priamo aj cez proxy – proxy môže chybu skryť (chyba 1) alebo pridať vlastnú.
