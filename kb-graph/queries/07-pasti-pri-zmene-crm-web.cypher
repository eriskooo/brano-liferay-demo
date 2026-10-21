// title: Na co si dát pozor, když měním crm-web
// teaches: WHERE se vzorem, výsledek jako tabulka
// Lekce ukotvené na modul crm-web, jeho endpointy, jejich soubory nebo službu, která se z něj builduje.
// To je otázka, kterou si klade agent před úpravou souboru: „co bych měl vědět o tom, na co sahám?“
MATCH (m:Module {id: 'crm-web'}), (l:Lesson)-[:ANCHORED_TO]->(x)
WHERE x = m
   OR (m)-[:DECLARES]->(x)
   OR (m)-[:DECLARES]->(:Endpoint)-[:DEFINED_IN]->(x)
   OR (x)-[:BUILT_FROM]->(m)
RETURN DISTINCT l.id AS lekce, l.title AS nazev, labels(x)[0] AS kotva, x.id AS na
ORDER BY lekce
