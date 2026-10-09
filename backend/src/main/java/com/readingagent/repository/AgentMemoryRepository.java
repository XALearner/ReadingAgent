package com.readingagent.repository;

import com.readingagent.domain.AgentMemory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentMemoryRepository extends JpaRepository<AgentMemory, Long> {
    Optional<AgentMemory> findByBookIdAndUserKey(Long bookId, String userKey);
    void deleteByBookId(Long bookId);
}
