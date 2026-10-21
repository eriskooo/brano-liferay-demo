// title: Co spadne, když spadne Redis?
// teaches: proměnná délka cesty *, tranzitivní závislosti
// Hvězdička znamená „přes libovolný počet kroků“. Dotaz najde i nepřímé závislé – nginx na Redis sám
// nezávisí, ale závisí na crm-web, který ano. Grep tohle neodpoví jedním příkazem.
MATCH p = (s:Service)-[:DEPENDS_ON*]->(:Service {id: 'redis'})
RETURN p
