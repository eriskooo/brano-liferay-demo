package com.example.crm.frontend.backend.blacklist;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** DAO tabuľky blacklist_identifier. */
class BlacklistIdentifierDao {

    private static final RowMapper<Identifier> MAPPER = new RowMapper<Identifier>() {
        @Override
        public Identifier mapRow(ResultSet rs, int row) throws SQLException {
            return new Identifier(rs.getLong("id"), IdentifierType.valueOf(rs.getString("type")), rs.getString("value"));
        }
    };

    private final NamedParameterJdbcTemplate jdbc;

    BlacklistIdentifierDao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Identifikátory záznamu v poradí vloženia. */
    List<Identifier> findByRecord(long recordId) {
        return jdbc.query("SELECT id, type, value FROM blacklist.blacklist_identifier WHERE record_id = :id ORDER BY id",
                new MapSqlParameterSource("id", recordId), MAPPER);
    }

    /** Vloží identifikátor k záznamu. */
    void insert(long recordId, IdentifierType type, String value) {
        jdbc.update("INSERT INTO blacklist.blacklist_identifier (record_id, type, value) VALUES (:recordId, :type, :value)",
                new MapSqlParameterSource("recordId", recordId).addValue("type", type.name()).addValue("value", value));
    }

    /** Zmaže identifikátor podľa ID. */
    void delete(long identifierId) {
        jdbc.update("DELETE FROM blacklist.blacklist_identifier WHERE id = :id",
                new MapSqlParameterSource("id", identifierId));
    }
}
