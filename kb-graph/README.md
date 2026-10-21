# Znalostní báze projektu v grafu (Neo4j)

> **Bonus – není součástí projektu migrace.** Samostatné učební demo nad tímto repem: nic z migrace na něm
> nezávisí, nepotřebuje běžící stack (`docker/`) a lze ho kdykoli smazat.

Učební demo: jak vypadá znalostní báze tohoto repa, když ji místo textu držíme jako **graf** v Neo4j, a proč se to
vyplatí lidem i agentům. Inspirace: článek kolegy *„Moving a project's knowledge base into a graph, and why it pays
off for me and for the agents“* (Medium). Demo přenáší jeho čtyři hlavní myšlenky:

1. **Pravda je JSON v gitu, Neo4j je jen lupa.** Databáze se plní z JSON a dá se kdykoli zahodit. Nic se do ní nepíše
   ručně – další `load` by to smazal.
2. **Struktura se generuje z kódu** (extraktor), takže nemůže zastarat.
3. **Lekce píše člověk** a ukotví je na konkrétní kus systému (modul, službu, endpoint…). Najdou se pak podle toho,
   na čem právě pracuji.
4. **Uložené dotazy** odpovídají na otázky přes několik kroků („co všechno spadne, když…“), na které grep nestačí.

## Návod krok za krokem

Předpoklady: Docker Desktop, JDK 21, Maven 3.9 (Git Bash nebo PowerShell). Vše z kořene repa.

1. **Spusť Neo4j** (a malý server pro guide) – kontejnery jsou dostupné jen z localhostu:

   ```bash
   bash kb-graph/kb.sh up            # PowerShell: .\kb-graph\kb.ps1 up
   ```

2. **Naplň graf** – extrakce struktury z kódu, načtení lekcí, kontrola kotev, smazání a naplnění Neo4j, vygenerování
   guide. Na konci vypíše např. `Nahraté: 182 uzlov, 348 vzťahov`:

   ```bash
   bash kb-graph/kb.sh load
   ```

3. **Otevři Neo4j Browser** na http://localhost:7474 a přihlas se: uživatel `neo4j`, heslo `kbdemo123`
   (protokol `neo4j://`, adresa `localhost:7687` jsou předvyplněné).
4. **Projdi guide.** Po přihlášení se vlevo sám otevře panel *Znalostní báze v grafu* (12 kroků, tlačítka
   *Next* / *Previous*). Pokud ne, napiš do editoru nahoře `:play http://localhost:7475/kb.html` a spusť.
5. **Spusť dotaz.** U bloku s dotazem klikni na *Execute in Query tab* (▶) – výsledek se vykreslí vpravo jako graf.
   Záložky *Graph / Table / Raw* přepínají zobrazení, kliknutím na uzel uvidíš jeho vlastnosti.
   *Edit in Cypher editor* dotaz jen vloží do editoru – můžeš ho upravit a zkoušet vlastní varianty.
6. **Zkus vlastní dotaz**, např. `MATCH (n) RETURN labels(n)[0] AS label, count(*) ORDER BY label` (co v grafu je).
7. **Po změně kódu nebo lekcí** stačí znovu `bash kb-graph/kb.sh load` (databáze se vždy naplní znovu z JSON).
8. **Úklid:** `bash kb-graph/kb.sh down` (databáze nemá volume – zmizí, příští `load` ji vytvoří znovu).

Další příkazy: `bash kb-graph/kb.sh extract` (jen přegeneruje `graph/structure.json`), `check` (exit 1, když commitnutý
`structure.json` neodpovídá kódu), `guide` (přegeneruje `guide/kb.html`), `down`. Ve PowerShellu totéž přes
`.\kb-graph\kb.ps1 <příkaz>`. Testy: `mvn -q -B -f kb-graph/extractor/pom.xml verify` (potřebuje Docker – Testcontainers).

## Co je grafová databáze a kdy dává smysl

Relační databáze ukládá **tabulky**; vztahy jsou cizí klíče a dotaz přes vztahy je `JOIN`. Grafová databáze ukládá
**uzly a vztahy přímo**; dotaz popisuje **vzor**, který se v grafu hledá. Vyplatí se, když jsou hlavní otázky
o vztazích, a hlavně o vztazích přes víc kroků: „kdo na tom závisí, i nepřímo“, „jaká je cesta z A do B“,
„co je s tím spojené“. Přesně takové jsou otázky nad strukturou projektu.

| Pojem | Co to je | Příklad v tomto grafu |
|---|---|---|
| **uzel** (node) | věc | služba `redis`, modul `crm-web`, pasti č. 19 |
| **label** | druh uzlu (jako „tabulka“), v Browseru barva | `Service`, `Module`, `Lesson` |
| **vlastnost** (property) | hodnota na uzlu nebo vztahu | `image: "redis:7.4-alpine"`, `ports: "6379:6379"` |
| **vztah** (relationship) | orientovaná hrana mezi dvěma uzly | `(crm-web)-[:DEPENDS_ON]->(redis)` |
| **typ vztahu** | druh hrany | `DEPENDS_ON`, `ANCHORED_TO`, `REPLACED_BY` |
| **cesta** (path) | posloupnost uzlů a vztahů | `nginx → /crm/ → crm-web → postgres → tabulka` |

## Cypher v kostce

Cypher kreslí vzory „ASCII artem“: uzel je `(závorka)`, vztah `-[hranatá]->`.

```cypher
MATCH (s:Service {id: 'crm-web'})-[:DEPENDS_ON]->(d:Service)   // vzor: crm-web závisí na d
RETURN s, d                                                      // co vrátit (graf nebo tabulka)
```

| Konstrukce | Význam | Dotaz v guide |
|---|---|---|
| `MATCH (n:Label {k: v})` | najdi uzly podle labelu a vlastnosti | 1 |
| `-[r]-`, `-[:TYP]->` | vztah libovolný / daného typu a směru | 1, 2 |
| `p = (…)-[…]->(…)` | celá cesta jako proměnná | 3 |
| `-[:TYP*]->` | libovolný počet kroků (tranzitivně) | 4, 5, 11 |
| `OPTIONAL MATCH` | jako LEFT JOIN – vrátí i to, co vzor nesplní | 6, 8 |
| `WHERE` se vzorem, `WHERE NOT (…)` | podmínka na existenci / neexistenci vztahu | 7, 9 |
| `count(…)`, `ORDER BY`, `LIMIT` | agregace a řazení | 10 |

## Prohlídka dotazy (guide)

Dotazy leží v [`queries/`](queries) – jeden soubor = jeden slajd; komentář nahoře je text slajdu. Guide
[`guide/kb.html`](guide/kb.html) se z nich generuje (`kb.sh guide`), takže se se soubory nerozejde.

1. **Jeden uzel a jeho sousedé** – Redis a vše, co s ním souvisí: závislosti z compose i lekce ukotvené na Redis.
2. **Topologie běhu** – služby z `docker/docker-compose.yml` a jejich `depends_on`.
3. **Cesta požadavku** – strangler v jednom obrázku: `/crm/` do nové aplikace, `/*` do Liferay, obě do stejných tabulek.
4. **Co spadne, když spadne Redis?** – i nepřímé závislosti (nginx → crm-web → redis).
5. **Co přebuildovat** – moduly závislé na `sso-portal-api`, obdoba dotazu z článku.
6. **Pasti k session** – propojení vrstev: téma → lekce → kotva ve struktuře.
7. **Na co si dát pozor v crm-web** – lekce ukotvené na modul, jeho endpointy, soubory nebo službu; otázka, kterou si
   má klást agent před úpravou souboru.
8. **Koncept → náhrada** – mapa z `poznamky/05` jako graf, včetně výskytu v demu před a po.
9. **Koncepty mimo demo** – negace vzoru: co demo neukazuje a na reálném projektu se musí ověřit zvlášť.
10. **Nejprovázanější poznámky** – kde začít číst.
11. **Srovnání se SQL** – dotaz 4 v Cypheru vedle rekurzivního CTE.

## Model grafu

**Struktura** (generuje extraktor z kódu, [`graph/structure.json`](graph/structure.json)):

| Label | Zdroj | Vztahy |
|---|---|---|
| `Module`, `Library` | všechny `pom.xml` (mimo `input/`, `target/`, `src/test`) | `DEPENDS_ON`, `CHILD_OF` |
| `Service` | `docker/docker-compose.yml` | `DEPENDS_ON`, `BUILT_FROM` → `Module` |
| `Route` | `docker/nginx/default.conf` | `(nginx)-[:ROUTES]->`, `PROXIES_TO` → `Service`, `REDIRECTS_TO` → `Route` |
| `Portlet` | `legacy/**/WEB-INF/portlet.xml` | `(Module)-[:DECLARES]->` |
| `Endpoint`, `File` | `@GetMapping`/`@PostMapping` v `crm-web` | `(Module)-[:DECLARES]->`, `DEFINED_IN` → `File` |
| `Table` | `docker/postgres/init/01-*.sql` | `(postgres)-[:OWNS]->` |
| `Note` | `poznamky/*.md` | `LINKS_TO` (odkazy mezi poznámkami) |

**Lekce** (píše člověk, [`lessons/`](lessons)):

| Label | Zdroj | Vztahy |
|---|---|---|
| `Topic` | `topics.json` | – |
| `Lesson` | `pitfalls.json` – každá past z `poznamky/07` | `ABOUT` → `Topic`, `ANCHORED_TO` → uzel struktury, `DESCRIBED_IN` → `Note` |
| `Concept` | `concepts.json` – řádky `poznamky/05` | `REPLACED_BY` (legacy → target), `SEEN_IN` → uzel struktury, `DESCRIBED_IN` |

## Proč struktura z kódu a proč je databáze jednorazová

- **Struktura z kódu.** Kdo přidá službu do compose, endpoint do controlleru nebo modul do POM, uvidí ho po dalším
  `load` v grafu – nikdo nic nepřepisuje ručně. Extraktor čte soubory jako text/XML/YAML (ne kompilátor) a **selže
  nahlas**: když najde méně uzlů, než je minimum (např. < 5 modulů), graf nezapíše. Radši chyba než tiše prázdný graf.
- **`check` patří do CI.** Commitnutý `structure.json` je čitelný v diffu pull requestu. Na reálném projektu by
  `kb.sh check` běžel v CI a připomněl, že se struktura změnila. Tady build nepadá, aby změny v jiných podprojektech
  nerozbíjely `kb-graph`.
- **Jednorazová databáze.** Každý `load` smaže celou databázi a nahraje ji znovu z JSON. Úpravy přímo v Browseru
  přežijí jen do dalšího loadu – lekce se proto píšou do JSON, ne do Neo4j.

## Jak přidat lekci

1. Do `lessons/pitfalls.json` přidej záznam (id `pitfall-NN` podle čísla bodu v `poznamky/07-pasce.md`):

   ```json
   {"id": "pitfall-33", "title": "…", "text": "1–2 věty vlastními slovy.",
    "topics": ["session"], "anchors": [{"label": "Service", "id": "redis"}], "note": "07-pasce.md"}
   ```

2. `bash kb-graph/kb.sh load`. Kotva musí mířit na uzel, který ve struktuře existuje (id najdeš v
   `graph/structure.json` nebo dotazem 1). Když neexistuje, load skončí a databázi nechá beze změny:

   ```
   Neplatné kotvy v lekciách (databáza ostala bez zmeny):
     pitfall-33 -[ANCHORED_TO]-> Service:neexistuje
   ```

   To je efekt z článku: lekce ukotvená na něco, co už v kódu není, se ozve sama, místo aby tiše zastarala.
   Test `RealLessonsTest` navíc hlídá, že každá past z `poznamky/07` má svou lekci.

## Omezení dema

- Bez vrstvy symbolů (třídy, metody, kdo koho volá přes JavaParser) – na pochopení Neo4j zbytečně těžká.
- Bez napojení agenta přes MCP; agent zatím může graf použít jen přes `cypher-shell` nebo tento guide.
- Lekce pokrývají `poznamky/05` a `07`; ostatní poznámky jsou v grafu jen jako uzly `Note` s odkazy.
