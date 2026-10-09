package com.readingagent.repository;

import com.readingagent.domain.AgentSession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentSessionRepository extends JpaRepository<AgentSession, String> {
    List<AgentSession> findByBookIdAndUserKeyOrderByCreatedAtDesc(Long bookId, String userKey);
    List<AgentSession> findByBookId(Long bookId);
    void deleteByBookId(Long bookId);
}
