package com.example.kb.load;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.testcontainers.neo4j.Neo4jContainer;

/** Jeden Neo4j kontajner pre všetky testy (štart trvá desiatky sekúnd); rovnaký image ako kb-graph/docker-compose.yml. */
public final class Neo4jTestDatabase {

    public static final String PASSWORD = "kbdemo123";
    private static final Neo4jContainer CONTAINER = new Neo4jContainer("neo4j:5.26-community").withAdminPassword(PASSWORD);
    private static Driver driver;

    private Neo4jTestDatabase() {
    }

    /** Bolt URL bežiaceho kontajnera (naštartuje ho pri prvom volaní). */
    public static synchronized String boltUrl() {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
        return CONTAINER.getBoltUrl();
    }

    /** Zdieľaný driver k testovacej databáze. */
    public static synchronized Driver driver() {
        if (driver == null) {
            driver = GraphDatabase.driver(boltUrl(), AuthTokens.basic("neo4j", PASSWORD));
        }
        return driver;
    }

    /** Spočíta riadky jedného dotazu – pomôcka pre asserty. */
    public static long count(String cypher) {
        try (var session = driver().session()) {
            return session.run(cypher).single().get(0).asLong();
        }
    }
}
