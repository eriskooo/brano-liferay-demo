// title: Co přebuildovat po změně modulu
// teaches: proměnná délka cesty nad moduly Maven
// Obdoba dotazu z článku: po změně sso-portal-api je třeba přebuildovat všechny moduly, které na něm
// závisejí přímo i nepřímo. Hrany DEPENDS_ON vznikly z pom.xml.
MATCH p = (m:Module)-[:DEPENDS_ON*]->(:Module {id: 'sso-portal-api'})
RETURN p
