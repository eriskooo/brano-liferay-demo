// title: Topologie běhu
// teaches: filtrování podle labelu a typu vztahu, směr šipky
// Všechny služby z docker/docker-compose.yml a jejich depends_on. Graf vznikl z kódu – kdo změní compose,
// po dalším load uvidí změnu tady, aniž by cokoli psal ručně.
MATCH (a:Service)-[r:DEPENDS_ON]->(b:Service)
RETURN a, r, b
