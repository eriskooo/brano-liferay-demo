package com.example.crm.blacklist;

import com.example.crm.blacklist.jpa.BlacklistRecordEntity;

/**
 * Entity → doménový objekt portu. V balíku domény, lebo konštruktor a mutátory {@link BlacklistRecord} sú
 * package-private (mení ich len backend). Volá sa v transakcii služby – lazy kolekcie sa načítajú tu, nie v JSP.
 */
final class BlacklistEntityMapper {

    private BlacklistEntityMapper() {
    }

    static BlacklistRecord toDomain(BlacklistRecordEntity entity) {
        BlacklistRecord record = new BlacklistRecord(entity.getId(), entity.getFirstName(), entity.getLastName(),
                entity.getBirthDate(), entity.getState(), entity.getStateReason(), entity.getConditionType(),
                entity.getValidFrom(), entity.getValidTo());
        entity.getIdentifiers().forEach(i -> record.addIdentifier(new Identifier(i.getId(), i.getType(), i.getValue())));
        entity.getNotes().forEach(n -> record.addNote(new Note(n.getText(), n.getAuthor(), n.getCreatedAt())));
        return record;
    }
}
