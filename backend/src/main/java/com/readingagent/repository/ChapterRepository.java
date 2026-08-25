package com.readingagent.repository;

import com.readingagent.domain.Chapter;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    List<Chapter> findByBookIdOrderBySortOrderAsc(Long bookId);

    Optional<Chapter> findFirstByBookIdOrderBySortOrderAsc(Long bookId);

    void deleteByBookId(Long bookId);
}
