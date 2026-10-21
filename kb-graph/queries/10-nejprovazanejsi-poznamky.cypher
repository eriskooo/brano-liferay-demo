// title: Nejprovázanější poznámky
// teaches: agregace count, ORDER BY, LIMIT
// Kolik lekcí a odkazů míří na každou poznámku z poznamky/. Kde začít číst, když přicházíš na projekt.
MATCH (n:Note)
OPTIONAL MATCH (n)<-[r:DESCRIBED_IN|LINKS_TO]-()
RETURN n.id AS poznamka, n.title AS nazev, count(r) AS odkazu
ORDER BY odkazu DESC, poznamka
LIMIT 5
