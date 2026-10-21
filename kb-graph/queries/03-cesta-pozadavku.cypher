// title: Cesta požadavku od nginx po tabulku
// teaches: cesta (path) jako proměnná p, řetězení vzorů
// Strangler v jednom obrázku: nginx posílá /crm/ do nové aplikace a vše ostatní (/*) do Liferay, obě
// aplikace sahají do stejných tabulek sdílené databáze.
MATCH p = (:Service {id: 'nginx'})-[:ROUTES]->(:Route)-[:PROXIES_TO]->(:Service)
          -[:DEPENDS_ON]->(:Service {id: 'postgres'})-[:OWNS]->(:Table)
RETURN p
