// title: Legacy koncepty, které demo neukazuje
// teaches: negace vzoru WHERE NOT
// Koncepty bez hrany SEEN_IN – demo je nepokrývá, na reálném projektu je třeba je ověřit zvlášť.
MATCH (c:Concept {side: 'legacy'})
WHERE NOT (c)-[:SEEN_IN]->()
RETURN c.name AS koncept, c.description AS popis
ORDER BY koncept
