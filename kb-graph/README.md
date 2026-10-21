# Znalostní báze projektu v grafu (Neo4j) – návod k použití

> **Bonus – není součástí projektu migrace.** Samostatné učební demo: nic z migrace na něm nezávisí, nepotřebuje
> běžící stack (`docker/`) a lze ho kdykoli smazat.

Znalostní báze tohoto repa (struktura projektu + pasti a koncepty z [`poznamky/`](../poznamky/README.md)) uložená jako
**graf** v Neo4j. Místo čtení a grepování textu se ptáš dotazem a odpověď vidíš jako obrázek: co s čím souvisí,
co na čem závisí, na co si dát pozor, když něco měníš.

Jak graf vzniká a jak do něj přidat poznatek: generátor v [`extractor/`](extractor/README.md) (vlastní Maven projekt a návod).

## Spuštění

Předpoklady: Docker Desktop, JDK 21, Maven 3.9. Z kořene repa (PowerShell: `.\kb-graph\kb.ps1 up` / `load`):

```bash
bash kb-graph/kb.sh up && bash kb-graph/kb.sh load
```

Pak otevři http://localhost:7474 a přihlas se `neo4j` / `kbdemo123` (adresa `localhost:7687` je předvyplněná).
Úklid: `bash kb-graph/kb.sh down`.

## Prohlížení v Neo4j Browseru

- Po přihlášení se vlevo sám otevře **guide** *Znalostní báze v grafu* – 12 kroků s vysvětlením (*Next* / *Previous*).
  Pokud se neotevře, napiš do editoru nahoře `:play http://localhost:7475/kb.html`.
- U bloku s dotazem: **Execute in Query tab** (▶) dotaz spustí, **Edit in Cypher editor** ho jen vloží do editoru.
- Výsledek je vpravo jako graf. Záložky **Graph / Table / Raw** přepínají zobrazení. Kliknutím na uzel uvidíš jeho
  vlastnosti, dvojklikem rozbalíš sousedy.
- Vlastní dotaz napíšeš do editoru nahoře a spustíš tlačítkem *Run* (nebo Ctrl+Enter).

## Jak jsou informace uspořádané

Graf má dvě vrstvy. **Struktura** popisuje, z čeho se projekt skládá (vzniká z kódu). **Lekce** jsou poznatky, které
napsal člověk, a každá je **ukotvená** na konkrétní kus struktury – proto je najdeš podle toho, na čem pracuješ.

| Label (druh uzlu) | Co to je | Příklad `id` |
|---|---|---|
| `Module` | Maven modul | `crm-web`, `customercare-portlets` |
| `Service` | kontejner z `docker/docker-compose.yml` | `redis`, `crm-web`, `liferay` |
| `Route` | cesta v nginx | `/crm/`, `/web/crm/blacklist`, `/*` (vše ostatní) |
| `Portlet` | portlet staré aplikace | `blacklist`, `dashboard` |
| `Endpoint`, `File` | URL nové aplikace a soubor controlleru | `GET /crm/blacklist/search` |
| `Table` | tabulka sdílené databáze | `blacklist.blacklist_record` |
| `Note` | soubor z `poznamky/` | `07-pasce.md` |
| `Lesson` | past z `poznamky/07` (`title`, `text`, `source`) | `pitfall-19` |
| `Topic` | téma lekcí | `session`, `redis`, `cookies`, `security` |
| `Concept` | koncept z `poznamky/05`, `side` = `legacy` / `target` | `action-mapping` → `post-mapping-prg` |

| Vztah | Význam |
|---|---|
| `DEPENDS_ON` | závisí na (služba na službě, modul na modulu nebo knihovně) |
| `ROUTES`, `PROXIES_TO`, `REDIRECTS_TO` | nginx má trasu → trasa posílá na službu / přesměrovává |
| `BUILT_FROM`, `DECLARES`, `DEFINED_IN`, `OWNS` | služba se builduje z modulu, modul deklaruje portlet/endpoint, endpoint je v souboru, Postgres vlastní tabulku |
| `ABOUT`, `ANCHORED_TO`, `DESCRIBED_IN` | lekce patří k tématu, týká se uzlu struktury, je popsaná v poznámce |
| `REPLACED_BY`, `SEEN_IN` | legacy koncept → náhrada, kde se koncept v demu vyskytuje |

Každý uzel má vlastnost `id`; podle ní uzly hledáš.

## Jak vyhledávat (Cypher v kostce)

Dotazovací jazyk Cypher kreslí hledaný vzor „ASCII artem“: uzel je `(n:Label {vlastnost: hodnota})`,
vztah `-[:TYP]->`. `MATCH` hledá vzor, `WHERE` filtruje, `RETURN` říká, co vrátit.

| Zápis | Význam |
|---|---|
| `MATCH (n:Service {id: 'redis'})` | uzel podle labelu a vlastnosti |
| `(a)-[:DEPENDS_ON]->(b)` | vztah daného typu a směru; `-[r]-` libovolný vztah v obou směrech |
| `-[:DEPENDS_ON*]->` | přes libovolný počet kroků (i nepřímé závislosti) |
| `p = (…)-[…]->(…)` | celá cesta jako jeden výsledek |
| `OPTIONAL MATCH` | jako LEFT JOIN – vrátí i to, co druhou část vzoru nesplní |
| `WHERE NOT (c)-[:SEEN_IN]->()` | podmínka, že vztah neexistuje |
| `toLower(l.text) CONTAINS 'redis'` | fulltextové hledání v textu |
| `count(…)`, `ORDER BY`, `LIMIT` | agregace a řazení |

## Příklady dotazů

Zkopíruj do editoru a spusť. Dalších 11 dotazů s vysvětlením je v guide (soubory v [`queries/`](queries)).

**Co v grafu je** (tabulka počtů):
```cypher
MATCH (n) RETURN labels(n)[0] AS druh, count(*) AS pocet ORDER BY druh
```

**Všechno o jedné věci** – služba Redis a vše, co s ní souvisí:
```cypher
MATCH (n:Service {id: 'redis'})-[r]-(m) RETURN n, r, m
```

**Na co si dát pozor, když měním crm-web** (tabulka lekcí):
```cypher
MATCH (m:Module {id: 'crm-web'}), (l:Lesson)-[:ANCHORED_TO]->(x)
WHERE x = m OR (m)-[:DECLARES]->(x) OR (x)-[:BUILT_FROM]->(m)
RETURN DISTINCT l.id, l.title, l.text ORDER BY l.id
```

**Pasti k tématu** (graf téma → lekce → kotva):
```cypher
MATCH (t:Topic {id: 'session'})<-[:ABOUT]-(l:Lesson)
OPTIONAL MATCH (l)-[a:ANCHORED_TO]->(x)
RETURN t, l, a, x
```

**Hledání v textu lekcí**:
```cypher
MATCH (l:Lesson) WHERE toLower(l.text + ' ' + l.title) CONTAINS 'cookie'
RETURN l.id, l.title, l.source
```

**Co spadne, když spadne Redis** (i nepřímo):
```cypher
MATCH p = (:Service)-[:DEPENDS_ON*]->(:Service {id: 'redis'}) RETURN p
```

**Kudy jde požadavek** – nginx → aplikace → databáze (stará i nová aplikace míří do stejných tabulek):
```cypher
MATCH p = (:Service {id: 'nginx'})-[:ROUTES]->(:Route)-[:PROXIES_TO]->(:Service)
          -[:DEPENDS_ON]->(:Service {id: 'postgres'})-[:OWNS]->(:Table)
RETURN p
```

**Čím nahradit Liferay koncept**:
```cypher
MATCH (c:Concept {side: 'legacy'})-[:REPLACED_BY]->(t:Concept)
RETURN c.name AS legacy, t.name AS nahrada ORDER BY legacy
```

**Kde začít číst** – nejprovázanější poznámky:
```cypher
MATCH (n:Note) OPTIONAL MATCH (n)<-[r]-()
RETURN n.id, n.title, count(r) AS odkazu ORDER BY odkazu DESC LIMIT 5
```

## Graf je jen kopie

Databáze se při každém `kb.sh load` smaže a naplní znovu ze souborů v repu. Co upravíš přímo v Browseru, další load
zahodí – trvalé změny patří do zdrojových souborů, viz [`extractor/README.md`](extractor/README.md).
