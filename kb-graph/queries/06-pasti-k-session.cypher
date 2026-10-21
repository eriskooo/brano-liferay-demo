// title: Pasti k tématu session a kam míří
// teaches: OPTIONAL MATCH, propojení dvou vrstev grafu
// Ručně psané lekce (Lesson) visí na tématu (Topic) a jsou ukotvené na strukturu z kódu. OPTIONAL MATCH vrátí
// i lekce bez kotvy – ty se týkají jen původního projektu.
MATCH (t:Topic {id: 'session'})<-[:ABOUT]-(l:Lesson)
OPTIONAL MATCH (l)-[a:ANCHORED_TO]->(x)
RETURN t, l, a, x
