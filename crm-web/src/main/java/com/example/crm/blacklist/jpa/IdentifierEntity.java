package com.example.crm.blacklist.jpa;

import com.example.crm.blacklist.IdentifierType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Riadok tabuľky blacklist_identifier (legacy: vlastné DAO identifikátorov). */
@Entity
@Table(name = "blacklist_identifier", schema = "blacklist")
public class IdentifierEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    private BlacklistRecordEntity record;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private IdentifierType type;

    @Column(name = "value", nullable = false)
    private String value;

    /** Pre JPA. */
    protected IdentifierEntity() {
    }

    IdentifierEntity(BlacklistRecordEntity record, IdentifierType type, String value) {
        this.record = record;
        this.type = type;
        this.value = value;
    }

    public Long getId() { return id; }
    public IdentifierType getType() { return type; }
    public String getValue() { return value; }
}
