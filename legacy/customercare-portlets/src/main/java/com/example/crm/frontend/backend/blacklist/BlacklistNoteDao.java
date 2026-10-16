package com.example.crm.frontend.backend.blacklist;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** DAO tabuľky blacklist_note. */
class BlacklistNoteDao {

    private static final RowMapper<Note> MAPPER = new RowMapper<Note>() {
        @Override
        public Note mapRow(ResultSet rs, int row) throws SQLException {
            return new Note(rs.getString("text"), rs.getString("author"), rs.getObject("created_at", LocalDateTime.class));
        }
    };

    private final NamedParameterJdbcTemplate jdbc;

    BlacklistNoteDao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Poznámky záznamu v poradí vloženia. */
    List<Note> findByRecord(long recordId) {
        return jdbc.query("SELECT text, author, created_at FROM blacklist.blacklist_note WHERE record_id = :id ORDER BY id",
                new MapSqlParameterSource("id", recordId), MAPPER);
    }

    /** Vloží poznámku k záznamu. */
    void insert(long recordId, String text, String author, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO blacklist.blacklist_note (record_id, text, author, created_at) "
                        + "VALUES (:recordId, :text, :author, :createdAt)",
                new MapSqlParameterSource("recordId", recordId)
                        .addValue("text", text)
                        .addValue("author", author)
                        .addValue("createdAt", createdAt, Types.TIMESTAMP));
    }
}
