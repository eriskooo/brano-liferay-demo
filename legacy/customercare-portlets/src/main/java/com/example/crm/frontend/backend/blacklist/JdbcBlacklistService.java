package com.example.crm.frontend.backend.blacklist;

import com.example.crm.frontend.base.web.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Adaptér portu na zdieľanú DB backendu (schéma {@code blacklist}) – tú istú používa nová appka cez JPA.
 * Ako legacy backend: služba skladá DAO po tabuľkách v transakcii; pravidlá, ktoré in-memory chránil
 * {@code synchronized}, drží transakcia a zámok riadku záznamu.
 */
public class JdbcBlacklistService implements BlacklistService {

    private final BlacklistRecordDao records;
    private final BlacklistIdentifierDao identifiers;
    private final BlacklistNoteDao notes;
    private final TransactionTemplate tx;
    private final Clock clock;

    public JdbcBlacklistService(DataSource dataSource, Clock clock) {
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.records = new BlacklistRecordDao(jdbc);
        this.identifiers = new BlacklistIdentifierDao(jdbc);
        this.notes = new BlacklistNoteDao(jdbc);
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.clock = clock;
    }

    @Override
    public List<BlacklistRecord> search(BlacklistSearchCriteria criteria) {
        List<BlacklistRecord> found = records.search(criteria);
        for (BlacklistRecord record : found) {
            loadDetails(record);
        }
        return found;
    }

    @Override
    public Optional<BlacklistRecord> findById(long id) {
        List<BlacklistRecord> found = records.findById(id);
        return found.isEmpty() ? Optional.<BlacklistRecord>empty() : Optional.of(loadDetails(found.get(0)));
    }

    @Override
    public BlacklistRecord create(final NewBlacklistRecord data) {
        return tx.execute(status -> {
            long id = records.insert(data);
            identifiers.insert(id, data.getIdentifierType(), data.getIdentifierValue());
            return require(id);
        });
    }

    @Override
    public void change(long id, BlacklistState state, LocalDate validTo) {
        LocalDate effectiveValidTo = (state == BlacklistState.INACTIVE && validTo == null) ? LocalDate.now(clock) : validTo;
        if (records.updateState(id, state, effectiveValidTo) == 0) {
            throw new NotFoundException("záznam blacklistu " + id);
        }
    }

    @Override
    public void addNote(final long id, final String text, final String author) {
        tx.execute(status -> {
            lockRecord(id);
            notes.insert(id, text.trim(), author, LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES));
            return null;
        });
    }

    @Override
    public List<Identifier> removeIdentifier(final long recordId, final long identifierId) {
        return tx.execute(status -> {
            lockRecord(recordId);
            List<Identifier> current = identifiers.findByRecord(recordId);
            boolean owned = false;
            for (Identifier identifier : current) {
                if (identifier.getId() == identifierId) {
                    owned = true;
                }
            }
            if (!owned) {
                // Identifikátor iného záznamu = podvrhnuté ID → 404, nie zmena cudzích dát.
                throw new NotFoundException("identifikátor " + identifierId + " záznamu " + recordId);
            }
            if (current.size() == 1) {
                throw new BlacklistRuleException("blacklist.detail.lastIdentifier");
            }
            identifiers.delete(identifierId);
            return Collections.unmodifiableList(identifiers.findByRecord(recordId));
        });
    }

    @Override
    public List<ConditionType> allowedConditionTypes(StateReason reason) {
        return ConditionType.allowedFor(reason);
    }

    /** Zámok riadku: dve súbežné odstránenia nesmú zmazať oba posledné identifikátory (in-memory: synchronized). */
    private void lockRecord(long id) {
        if (!records.lock(id)) {
            throw new NotFoundException("záznam blacklistu " + id);
        }
    }

    private BlacklistRecord require(long id) {
        Optional<BlacklistRecord> record = findById(id);
        if (!record.isPresent()) {
            throw new NotFoundException("záznam blacklistu " + id);
        }
        return record.get();
    }

    private BlacklistRecord loadDetails(BlacklistRecord record) {
        for (Identifier identifier : identifiers.findByRecord(record.getId())) {
            record.addIdentifier(identifier);
        }
        for (Note note : notes.findByRecord(record.getId())) {
            record.addNote(note);
        }
        return record;
    }
}
