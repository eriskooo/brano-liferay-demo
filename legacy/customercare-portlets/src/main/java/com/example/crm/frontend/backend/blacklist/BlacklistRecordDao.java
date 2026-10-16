package com.example.crm.frontend.backend.blacklist;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** DAO tabuľky blacklist_record – ako v legacy backende: ručné SQL a RowMapper, jedno DAO na tabuľku. */
class BlacklistRecordDao {

    private static final String SELECT = "SELECT r.id, r.first_name, r.last_name, r.birth_date, r.state, "
            + "r.state_reason, r.condition_type, r.valid_from, r.valid_to FROM blacklist.blacklist_record r";

    private static final RowMapper<BlacklistRecord> MAPPER = new RowMapper<BlacklistRecord>() {
        @Override
        public BlacklistRecord mapRow(ResultSet rs, int row) throws SQLException {
            return new BlacklistRecord(rs.getLong("id"), rs.getString("first_name"), rs.getString("last_name"),
                    rs.getObject("birth_date", LocalDate.class), BlacklistState.valueOf(rs.getString("state")),
                    StateReason.valueOf(rs.getString("state_reason")),
                    ConditionType.valueOf(rs.getString("condition_type")),
                    rs.getObject("valid_from", LocalDate.class), rs.getObject("valid_to", LocalDate.class));
        }
    };

    private final NamedParameterJdbcTemplate jdbc;

    BlacklistRecordDao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Záznamy podľa kritérií (bez identifikátorov a poznámok). */
    List<BlacklistRecord> search(BlacklistSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (hasText(criteria.getFirstName())) {
            sql.append(" AND r.first_name ILIKE :firstName");
            params.addValue("firstName", containsPattern(criteria.getFirstName()));
        }
        if (hasText(criteria.getLastName())) {
            sql.append(" AND r.last_name ILIKE :lastName");
            params.addValue("lastName", containsPattern(criteria.getLastName()));
        }
        if (hasText(criteria.getIdentifierValue())) {
            sql.append(" AND EXISTS (SELECT 1 FROM blacklist.blacklist_identifier i"
                    + " WHERE i.record_id = r.id AND lower(i.value) = lower(:identifier))");
            params.addValue("identifier", criteria.getIdentifierValue().trim());
        }
        if (criteria.getState() != null) {
            sql.append(" AND r.state = :state");
            params.addValue("state", criteria.getState().name());
        }
        return jdbc.query(sql.append(" ORDER BY r.id").toString(), params, MAPPER);
    }

    /** Záznam podľa ID alebo prázdny zoznam. */
    List<BlacklistRecord> findById(long id) {
        return jdbc.query(SELECT + " WHERE r.id = :id", new MapSqlParameterSource("id", id), MAPPER);
    }

    /** Zamkne riadok záznamu do konca transakcie; false = záznam neexistuje. */
    boolean lock(long id) {
        return !jdbc.queryForList("SELECT id FROM blacklist.blacklist_record WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", id), Long.class).isEmpty();
    }

    /** Vloží aktívny záznam a vráti jeho ID. */
    long insert(NewBlacklistRecord data) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("firstName", data.getFirstName())
                .addValue("lastName", data.getLastName())
                .addValue("birthDate", data.getBirthDate(), Types.DATE)
                .addValue("state", BlacklistState.ACTIVE.name())
                .addValue("stateReason", data.getStateReason().name())
                .addValue("conditionType", data.getConditionType().name())
                .addValue("validFrom", data.getValidFrom(), Types.DATE)
                .addValue("validTo", data.getValidTo(), Types.DATE);
        return jdbc.queryForObject("INSERT INTO blacklist.blacklist_record (first_name, last_name, birth_date, state, "
                + "state_reason, condition_type, valid_from, valid_to) VALUES (:firstName, :lastName, :birthDate, "
                + ":state, :stateReason, :conditionType, :validFrom, :validTo) RETURNING id", params, Long.class);
    }

    /** Zmení stav a platnosť; vráti počet zmenených riadkov (0 = záznam neexistuje). */
    int updateState(long id, BlacklistState state, LocalDate validTo) {
        return jdbc.update("UPDATE blacklist.blacklist_record SET state = :state, valid_to = :validTo WHERE id = :id",
                new MapSqlParameterSource("state", state.name())
                        .addValue("validTo", validTo, Types.DATE)
                        .addValue("id", id));
    }

    /** Používateľ hľadá text, nie LIKE vzor – % a _ sa escapujú (Postgres má \ ako default ESCAPE). */
    private static String containsPattern(String query) {
        String escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
