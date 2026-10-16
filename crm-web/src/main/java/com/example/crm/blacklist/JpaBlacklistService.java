package com.example.crm.blacklist;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import com.example.crm.blacklist.jpa.BlacklistRecordEntity;
import com.example.crm.blacklist.jpa.BlacklistRecordRepository;
import com.example.crm.blacklist.jpa.BlacklistSpecifications;
import com.example.crm.blacklist.jpa.IdentifierEntity;
import com.example.crm.common.NotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptér portu na zdieľanú DB backendu cez JPA. Legacy: JDBC DAO po tabuľkách nad tou istou schémou – migrácia
 * mení spôsob prístupu, nie dáta ani pravidlá. Pravidlá, ktoré in-memory chránil {@code synchronized},
 * drží transakcia a zámok riadku záznamu.
 */
@Service
@ConditionalOnProperty(name = "crm.blacklist.store", havingValue = "jpa")
@Transactional
public class JpaBlacklistService implements BlacklistService {

    private final BlacklistRecordRepository records;
    private final Clock clock;

    public JpaBlacklistService(BlacklistRecordRepository records, Clock clock) {
        this.records = records;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BlacklistRecord> search(BlacklistSearchCriteria criteria) {
        return records.findAll(BlacklistSpecifications.matching(criteria), Sort.by("id")).stream()
                .map(BlacklistEntityMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BlacklistRecord> findById(long id) {
        return records.findById(id).map(BlacklistEntityMapper::toDomain);
    }

    @Override
    public BlacklistRecord create(NewBlacklistRecord data) {
        BlacklistRecordEntity entity = new BlacklistRecordEntity(data.firstName(), data.lastName(), data.birthDate(),
                BlacklistState.ACTIVE, data.stateReason(), data.conditionType(), data.validFrom(), data.validTo());
        entity.addIdentifier(data.identifierType(), data.identifierValue());
        // saveAndFlush: IDENTITY pridelí ID záznamu aj identifikátora hneď, doména ich dostane vyplnené.
        return BlacklistEntityMapper.toDomain(records.saveAndFlush(entity));
    }

    @Override
    public void change(long id, BlacklistState state, LocalDate validTo) {
        LocalDate effectiveValidTo = (state == BlacklistState.INACTIVE && validTo == null) ? LocalDate.now(clock) : validTo;
        require(id).changeState(state, effectiveValidTo);
    }

    @Override
    public void addNote(long id, String text, String author) {
        lockRecord(id).addNote(text.trim(), author, LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES));
    }

    @Override
    public List<Identifier> removeIdentifier(long recordId, long identifierId) {
        BlacklistRecordEntity record = lockRecord(recordId);
        IdentifierEntity identifier = record.getIdentifiers().stream()
                .filter(i -> i.getId() == identifierId)
                .findFirst()
                // Identifikátor iného záznamu = podvrhnuté ID → 404, nie zmena cudzích dát.
                .orElseThrow(() -> new NotFoundException("identifikátor " + identifierId + " záznamu " + recordId));
        if (record.getIdentifiers().size() == 1) {
            throw new BlacklistRuleException("blacklist.detail.lastIdentifier");
        }
        record.removeIdentifier(identifier);
        records.flush();
        return BlacklistEntityMapper.toDomain(record).getIdentifiers();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConditionType> allowedConditionTypes(StateReason reason) {
        return ConditionType.allowedFor(reason);
    }

    private BlacklistRecordEntity require(long id) {
        return records.findById(id).orElseThrow(() -> new NotFoundException("záznam blacklistu " + id));
    }

    private BlacklistRecordEntity lockRecord(long id) {
        return records.findForUpdate(id).orElseThrow(() -> new NotFoundException("záznam blacklistu " + id));
    }
}
