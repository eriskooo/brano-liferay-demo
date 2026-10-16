package com.example.crm.blacklist.jpa;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Riadok tabuľky blacklist_note (legacy: vlastné DAO poznámok). */
@Entity
@Table(name = "blacklist_note", schema = "blacklist")
public class NoteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    private BlacklistRecordEntity record;

    @Column(name = "text", nullable = false)
    private String text;

    @Column(name = "author", nullable = false)
    private String author;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Pre JPA. */
    protected NoteEntity() {
    }

    NoteEntity(BlacklistRecordEntity record, String text, String author, LocalDateTime createdAt) {
        this.record = record;
        this.text = text;
        this.author = author;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getText() { return text; }
    public String getAuthor() { return author; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
