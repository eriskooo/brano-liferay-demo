// title: Legacy koncept → náhrada a kde je vidět
// teaches: více OPTIONAL MATCH, vlastnost v podmínce
// Mapa konceptů z poznamky/05 jako graf: koncept Liferay (side legacy) vede přes REPLACED_BY na náhradu
// ve Spring Boot a SEEN_IN ukazuje, kde se v demu vyskytuje před a po migraci.
MATCH (c:Concept {side: 'legacy'})-[r:REPLACED_BY]->(t:Concept)
OPTIONAL MATCH (c)-[s1:SEEN_IN]->(pred)
OPTIONAL MATCH (t)-[s2:SEEN_IN]->(po)
RETURN c, r, t, s1, pred, s2, po
