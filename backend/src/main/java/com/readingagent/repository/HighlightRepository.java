package com.readingagent.repository;

import com.readingagent.domain.Highlight;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HighlightRepository extends JpaRepository<Highlight, Long> {
    List<Highlight> findByBookIdOrderByCreatedAtDesc(Long bookId);

    void deleteByBookId(Long bookId);
}
