# Generátor grafu (kb-graph-extractor)

Maven projekt ([`pom.xml`](pom.xml), Java 21), který z kódu repa vygeneruje strukturu projektu, přidá ručně psané
lekce a nahraje vše do Neo4j. Návod k prohlížení a dotazům: [../README.md](../README.md).

Inspirace: článek kolegy *„Moving a project's knowledge base into a graph, and why it pays off for me and for the
agents“* (Medium). Generátor přenáší jeho hlavní myšlenky:

1. **Pravda je JSON v gitu, Neo4j je jen lupa.** Databáze se plní ze souborů a dá se kdykoli zahodit.
2. **Struktura se generuje z kódu**, takže nemůže zastarat – nikdo ji nepřepisuje ručně.
3. **Lekce píše člověk** a ukotví je na konkrétní uzel struktury. Lekce ukotvená na něco, co už v kódu není,
   se při načtení ozve sama.

## Generování = jeden krok

Z kořene repa, s běžícím Neo4j (`bash kb-graph/kb.sh up`):

```bash
bash kb-graph/kb.sh load        # PowerShell: .\kb-graph\kb.ps1 load
```

`load` udělá všechno najednou:

1. přeloží generátor (`mvn package`), pokud se změnily zdrojáky,
2. vygeneruje strukturu z kódu do [`../graph/structure.json`](../graph/structure.json),
3. načte lekce z [`../lessons/`](../lessons) a ověří, že každá kotva míří na existující uzel – jinak skončí chybou
   a databázi nechá beze změny,
4. smaže Neo4j a nahraje do ní strukturu + lekce (vypíše např. `Nahraté: 182 uzlov, 348 vzťahov`),
5. vygeneruje guide [`../guide/kb.html`](../guide/kb.html) z dotazů v [`../queries/`](../queries).

## Build a přímé použití

Skripty `kb.sh` / `kb.ps1` jsou jen obálka; generátor jde použít i přímo:

```bash
mvn -q -B -f kb-graph/extractor/pom.xml package -DskipTests      # → target/kb-graph-extractor.jar
java -jar kb-graph/extractor/target/kb-graph-extractor.jar <příkaz> [volby]
```

| Příkaz | Co dělá | Exit kód |
|---|---|---|
| `extract` | vygeneruje `kb-graph/graph/structure.json` z kódu | 0, 1 = extraktor našel příliš málo uzlů |
| `check` | porovná commitnutý `structure.json` s kódem (na reálném projektu patří do CI) | 0 aktuální, 1 zastaralý |
| `load` | `extract` + lekce, ověření kotev, smazání a naplnění Neo4j | 0, 1 = neplatné kotvy / chybný vstup, 2 = Neo4j nedostupné nebo špatné heslo |
| `guide` | vygeneruje `kb-graph/guide/kb.html` z `kb-graph/queries/*.cypher` | 0 |

| Volba | Výchozí hodnota |
|---|---|
| `--repo <cesta>` | aktuální adresář (kořen repa) |
| `--out <soubor>` | `kb-graph/graph/structure.json` |
| `--lessons <adresář>` | `kb-graph/lessons` |
| `--uri`, `--user`, `--password` | `bolt://localhost:7687`, `neo4j`, `kbdemo123` |

Skripty navíc nabízejí `up` / `down` (Neo4j a server pro guide v Dockeru, jen localhost, bez volume).

## Odkud se data berou

**Struktura** – extraktor čte soubory repa jako text/XML/YAML (ne kompilátor):

| Uzly | Zdroj | Vztahy |
|---|---|---|
| `Module`, `Library` | všechny `pom.xml` (mimo `input/`, `target/`, `src/test`) | `DEPENDS_ON`, `CHILD_OF` |
| `Service` | `docker/docker-compose.yml` | `DEPENDS_ON`, `BUILT_FROM` → `Module` |
| `Route` | `docker/nginx/default.conf` | `(nginx)-[:ROUTES]->`, `PROXIES_TO`, `REDIRECTS_TO` |
| `Portlet` | `legacy/**/WEB-INF/portlet.xml` | `(Module)-[:DECLARES]->` |
| `Endpoint`, `File` | `@GetMapping` / `@PostMapping` v `crm-web` | `(Module)-[:DECLARES]->`, `DEFINED_IN` |
| `Table` | `docker/postgres/init/01-*.sql` | `(postgres)-[:OWNS]->` |
| `Note` | `poznamky/*.md` | `LINKS_TO` |

Extraktor **selže nahlas**: když najde méně uzlů než minimum (např. < 5 modulů), graf nezapíše – radši chyba než
tiše prázdný graf po přesunu souboru.

**Lekce** – ručně psané JSON v [`../lessons/`](../lessons):

| Soubor | Uzly | Vztahy |
|---|---|---|
| `topics.json` | `Topic` | – |
| `pitfalls.json` | `Lesson` – každá past z `poznamky/07-pasce.md` | `ABOUT`, `ANCHORED_TO`, `DESCRIBED_IN` |
| `concepts.json` | `Concept` – řádky `poznamky/05-mapa-konceptov.md` | `REPLACED_BY`, `SEEN_IN`, `DESCRIBED_IN` |

## Jak přidat lekci

1. Do `kb-graph/lessons/pitfalls.json` přidej záznam (id `pitfall-NN` podle čísla bodu v `poznamky/07-pasce.md`):

   ```json
   {"id": "pitfall-33", "title": "…", "text": "1–2 věty vlastními slovy.",
    "topics": ["session"], "anchors": [{"label": "Service", "id": "redis"}], "note": "07-pasce.md"}
   ```

2. `bash kb-graph/kb.sh load`. Id kotvy najdeš v `graph/structure.json` nebo dotazem v Browseru. Neexistující kotva:

   ```
   Neplatné kotvy v lekciách (databáza ostala bez zmeny):
     pitfall-33 -[ANCHORED_TO]-> Service:neexistuje
   ```

Test `RealLessonsTest` hlídá, že každá past z `poznamky/07` má svou lekci – nová past bez lekce shodí testy.

## Nový uložený dotaz

Přidej soubor `kb-graph/queries/NN-nazev.cypher`. Hlavička v komentářích je text slajdu:

```cypher
// title: Název slajdu
// teaches: jaký pojem Cypheru ukazuje
// Jedna nebo více vět vysvětlení.
MATCH (n:Service) RETURN n
```

`kb.sh load` (nebo `kb.sh guide`) ho přidá do guide. Test `QueriesTest` ověří, že každý dotaz nad skutečným grafem
vrací aspoň jeden řádek.

## Struktura kódu

Balík `com.example.kb` ([`src/main/java`](src/main/java/com/example/kb)):

| Balík | Zodpovědnost |
|---|---|
| `Main` | příkazová řádka (`extract`, `check`, `load`, `guide`) |
| `graph` | model grafu (`Node`, `Edge`, `Graph`), skládání bez duplicit (`GraphBuilder`), deterministický JSON (`GraphJson`) |
| `extract` | jeden extraktor na zdroj (`PomExtractor`, `ComposeExtractor`, `NginxExtractor`, …), `StructureExtractor` je spustí a hlídá minima |
| `lessons` | čtení lekcí (`LessonReader`), ověření kotev (`AnchorValidator`) |
| `load` | příkaz `load` a nahrání do Neo4j (`Neo4jLoader`) |
| `guide` | uložené dotazy (`SavedQuery`) → HTML guide (`GuideGenerator`) |

Nový zdroj faktů = nová třída implementující `Extractor`, zařazená do `StructureExtractor`.

## Testy

```bash
mvn -q -B -f kb-graph/extractor/pom.xml verify     # potřebuje Docker (Testcontainers Neo4j)
```

Testy extraktorů běží na malých vstupech v `src/test/resources/fixtures`, další nad skutočným repem (minima,
klíčové uzly, determinismus, lekce proti struktuře) a nad Neo4j v kontejneru (load, opakovaný load, chybné kotvy,
nedostupná databáze, všechny uložené dotazy).

## Omezení

- Bez vrstvy symbolů (třídy, metody, kdo koho volá) – na pochopení Neo4j zbytečně těžká.
- Bez napojení agenta přes MCP; agent může graf použít přes `cypher-shell`.
- Lekce pokrývají `poznamky/05` a `07`; ostatní poznámky jsou v grafu jen jako uzly `Note` s odkazy.
