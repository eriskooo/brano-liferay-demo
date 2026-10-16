package com.example.crm.e2e.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** DbProbe nad JDBC (superuser kvôli resetu). Spojenie na každé volanie – testy volajú pár dotazov, jednoduchosť vyhráva. */
public final class JdbcDbProbe implements DbProbe {

    private final E2eConfig config;

    public JdbcDbProbe(E2eConfig config) {
        this.config = config;
    }

    @Override
    public void reset() {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(config.seedScript(), StandardCharsets.UTF_8));
        } catch (SQLException e) {
            throw new IllegalStateException("Reset DB zlyhal (beží postgres? " + config.jdbcUrl() + ")", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Seed " + config.seedScript() + " sa nedá prečítať", e);
        }
    }

    @Override
    public Optional<RecordRow> record(long id) {
        return query("SELECT id, first_name, last_name, state, valid_to FROM blacklist.blacklist_record WHERE id = ?",
                rs -> new RecordRow(rs.getLong("id"), rs.getString("first_name"), rs.getString("last_name"),
                        rs.getString("state"), rs.getObject("valid_to", LocalDate.class)), id).stream().findFirst();
    }

    @Override
    public List<IdentifierRow> identifiers(long recordId) {
        return query("SELECT id, type, value FROM blacklist.blacklist_identifier WHERE record_id = ? ORDER BY id",
                rs -> new IdentifierRow(rs.getLong("id"), rs.getString("type"), rs.getString("value")), recordId);
    }

    @Override
    public List<NoteRow> notes(long recordId) {
        return query("SELECT text, author FROM blacklist.blacklist_note WHERE record_id = ? ORDER BY id",
                rs -> new NoteRow(rs.getString("text"), rs.getString("author")), recordId);
    }

    @Override
    public int recordCount() {
        return query("SELECT count(*) FROM blacklist.blacklist_record", rs -> rs.getInt(1)).get(0);
    }

    @Override
    public LocalDate today() {
        return query("SELECT current_date", rs -> rs.getObject(1, LocalDate.class)).get(0);
    }

    private <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Dotaz zlyhal: " + sql, e);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(config.jdbcUrl(), config.jdbcUser(), config.jdbcPassword());
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }
}
