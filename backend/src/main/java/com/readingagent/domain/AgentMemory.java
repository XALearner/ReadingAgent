package com.readingagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "agent_memories", uniqueConstraints = @UniqueConstraint(name = "uk_agent_memory_owner", columnNames = {"book_id", "user_key"}))
public class AgentMemory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Book book;

    @Column(name = "user_key", nullable = false, length = 100)
    private String userKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }
    public String getUserKey() { return userKey; }
    public void setUserKey(String userKey) { this.userKey = userKey; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; this.updatedAt = Instant.now(); }
}
