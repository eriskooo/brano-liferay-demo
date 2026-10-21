package com.example.kb.load;

import com.example.kb.graph.Edge;
import com.example.kb.graph.Graph;
import com.example.kb.graph.Node;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionContext;

/**
 * Naplní Neo4j z grafu. Databáza je jednorazová „lupa“ (podľa článku): každé nahratie ju celú premaže,
 * takže v nej nikdy nezostane nič, čo nie je v JSON – ručné úpravy v Browseri ďalší load zmaže.
 */
public class Neo4jLoader {

    /** Labely a typy sa do Cypheru dosadzujú ako text (parametrom to nejde) – preto len bezpečné identifikátory. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final Driver driver;

    public Neo4jLoader(Driver driver) {
        this.driver = driver;
    }

    /** Zmaže celú databázu a nahrá do nej graf; vráti počty uzlov a vzťahov po nahratí. */
    public LoadResult load(Graph graph) {
        try (Session session = driver.session()) {
            createIndexes(session, graph);
            session.executeWriteWithoutResult(tx -> {
                tx.run("MATCH (n) DETACH DELETE n");
                createNodes(tx, graph.nodes());
                createEdges(tx, graph.edges());
            });
            long nodes = session.run("MATCH (n) RETURN count(n)").single().get(0).asLong();
            long relationships = session.run("MATCH ()-[r]->() RETURN count(r)").single().get(0).asLong();
            return new LoadResult(nodes, relationships);
        }
    }

    private static void createIndexes(Session session, Graph graph) {
        // Index na id – hrany sa pripájajú cez MATCH podľa id; schéma sa mení mimo dátovej transakcie.
        graph.nodes().stream().map(Node::label).distinct().forEach(label ->
                session.run("CREATE INDEX kb_" + safe(label) + "_id IF NOT EXISTS FOR (n:" + safe(label) + ") ON (n.id)"));
    }

    private static void createNodes(TransactionContext tx, List<Node> nodes) {
        Map<String, List<Node>> byLabel = nodes.stream()
                .collect(Collectors.groupingBy(Node::label, TreeMap::new, Collectors.toList()));
        byLabel.forEach((label, group) -> {
            List<Map<String, Object>> rows = group.stream()
                    .map(n -> Map.<String, Object>of("id", n.id(), "props", new HashMap<>(n.props())))
                    .toList();
            tx.run("UNWIND $rows AS r CREATE (n:" + safe(label) + " {id: r.id}) SET n += r.props",
                    Map.of("rows", rows));
        });
    }

    private static void createEdges(TransactionContext tx, List<Edge> edges) {
        // Zoskupenie podľa (label zdroja, typ, label cieľa) – jeden UNWIND dotaz na skupinu.
        Map<List<String>, List<Edge>> groups = edges.stream()
                .collect(Collectors.groupingBy(e -> List.of(e.from().label(), e.type(), e.to().label())));
        groups.forEach((key, group) -> {
            List<Map<String, Object>> rows = group.stream()
                    .map(e -> Map.<String, Object>of("from", e.from().id(), "to", e.to().id()))
                    .toList();
            tx.run("UNWIND $rows AS r MATCH (a:" + safe(key.get(0)) + " {id: r.from}), (b:" + safe(key.get(2))
                    + " {id: r.to}) CREATE (a)-[:" + safe(key.get(1)) + "]->(b)", Map.of("rows", rows));
        });
    }

    private static String safe(String identifier) {
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Neplatný label alebo typ vzťahu: " + identifier);
        }
        return identifier;
    }
}
