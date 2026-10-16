package com.example.crm.frontend.backend.blacklist;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/** Skutočný Postgres s init skriptmi z docker/postgres/init – jeden zdroj schémy a seedu pre obe appky. */
final class PostgresTestDatabase {

    private static final Path INIT = Paths.get("..", "..", "docker", "postgres", "init").toAbsolutePath().normalize();
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

    void start() {
        container.start();
    }

    void stop() {
        container.stop();
    }

    /** DataSource ako appka – používateľ crm_app len s DML právami. */
    DataSource appDataSource() {
        return dataSource("crm_app", "crm_app");
    }

    /** Vráti DB do stavu seedu. */
    void resetSeed() {
        try (Connection connection = dataSource("postgres", "postgres").getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(new String(Files.readAllBytes(INIT.resolve(SEED_SCRIPT)), StandardCharsets.UTF_8));
        } catch (SQLException e) {
            throw new IllegalStateException("Reset seedu zlyhal", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private DataSource dataSource(String user, String password) {
        String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/crm";
        return new DriverManagerDataSource(url, user, password);
    }
}
