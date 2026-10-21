package com.example.kb.load;

/** Počty v Neo4j po nahratí – na kontrolu, že databáza zodpovedá JSON. */
public record LoadResult(long nodes, long relationships) {
}
