package com.readingagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_sessions", indexes = @Index(name = "idx_agent_sessions_owner", columnList = "book_id,user_key"))
public class AgentSession {
    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Book book;

    @Column(name = "user_key", nullable = false, length = 100)
    private String userKey;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }
    public String getUserKey() { return userKey; }
    public void setUserKey(String userKey) { this.userKey = userKey; }
    public Instant getCreatedAt() { return createdAt; }
}
