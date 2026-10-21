// title: Srovnání se SQL
// teaches: proč graf – stejná otázka v Cypheru a v SQL
// Otázka z dotazu 4 jako tabulka jmen. V relační databázi by hrany byly tabulka depends_on(source, target)
// a tranzitivní uzávěr by potřeboval rekurzivní CTE – v Cypheru stačí hvězdička.
// sql: WITH RECURSIVE dependants(id) AS (
// sql:     SELECT source FROM depends_on WHERE target = 'redis'
// sql:   UNION
// sql:     SELECT d.source FROM depends_on d JOIN dependants x ON d.target = x.id
// sql: )
// sql: SELECT id FROM dependants ORDER BY id;
MATCH (s:Service)-[:DEPENDS_ON*]->(:Service {id: 'redis'})
RETURN DISTINCT s.id AS sluzba
ORDER BY sluzba
