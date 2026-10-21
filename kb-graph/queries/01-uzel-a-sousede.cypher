// title: Jeden uzel a jeho sousedé
// teaches: MATCH, uzel s labelem a vlastností, vztah bez směru
// Začni u služby Redis: kdo na ni míří a kam míří ona. Uvidíš uzly různých labelů (Service, Lesson, Concept)
// a šipky s typem vztahu – závislosti z docker-compose i lekce, které jsou na Redis ukotvené.
MATCH (n:Service {id: 'redis'})-[r]-(m)
RETURN n, r, m
