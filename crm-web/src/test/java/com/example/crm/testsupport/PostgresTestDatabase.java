package com.example.crm.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * Skutočný Postgres s tými istými init skriptmi ako docker-compose – schéma, rola aj seed majú jeden zdroj.
 * Appka sa pripája ako crm_app (overí aj práva), reset robí superuser.
 */
public final class PostgresTestDatabase {

    private static final Path INIT = Path.of("..", "docker", "postgres", "init").toAbsolutePath().normalize();
    private static final String SCHEMA_SCRIPT = "01-blacklist-schema.sql";
    private static final String SEED_SCRIPT = "02-blacklist-seed.sql";

    private final GenericContainer<?> container = new GenericContainer<>("postgres:16-alpine")
            .withEnv("POSTGRES_DB", "crm")
            .withEnv("POSTGRES_USER", "postgres")
            .withEnv("POSTGRES_PASSWORD", "postgres")
            .withCopyFileToContainer(MountableFile.forHostPath(INIT.resolve(SCHEMA_SCRIPT)),
                    "/docker-entrypoint-initdb.d/" + SCHEMA_SCRIPT)
            .withCopyFileToContainer(MountableFile.forHostPath(INIT.resolve(SEED_SCRIPT)),
                    "/docker-entrypoint-initdb.d/" + SEED_SCRIPT)
            .withExposedPorts(5432)
            // Hláška je dvakrát: dočasný server počas initu a potom ostrý.
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2));

    /** Spustí kontajner (init skripty prebehnú pri prvom štarte). */
    public void start() {
        container.start();
    }

    /** Zastaví kontajner. */
    public void stop() {
        container.stop();
    }

    /** JDBC URL bežiaceho kontajnera (pre spring.datasource.url). */
    public String jdbcUrl() {
        return "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/crm";
    }

    /** DataSource ako appka – používateľ crm_app len s DML právami. */
    public DataSource appDataSource() {
        return new DriverManagerDataSource(jdbcUrl(), "crm_app", "crm_app");
    }

    /** Vráti DB do stavu seedu (rovnaký skript ako init a e2e reset). */
    public void resetSeed() {
        try (Connection connection = new DriverManagerDataSource(jdbcUrl(), "postgres", "postgres").getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(INIT.resolve(SEED_SCRIPT), StandardCharsets.UTF_8));
        } catch (SQLException e) {
            throw new IllegalStateException("Reset seedu zlyhal", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
