package com.readingagent.repository;

import com.readingagent.domain.ReadingProgress;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, Long> {
    Optional<ReadingProgress> findByBookIdAndUserKey(Long bookId, String userKey);
}
