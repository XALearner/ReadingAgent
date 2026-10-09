package com.readingagent.repository;

import com.readingagent.domain.AgentMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface AgentMessageRepository extends JpaRepository<AgentMessage, Long> {
    List<AgentMessage> findBySessionIdOrderByIdAsc(String sessionId);
    List<AgentMessage> findBySessionIdOrderByIdDesc(String sessionId, Pageable pageable);
    void deleteBySessionId(String sessionId);
}
