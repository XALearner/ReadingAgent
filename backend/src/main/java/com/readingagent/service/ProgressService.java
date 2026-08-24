package com.readingagent.service;

import com.readingagent.domain.Book;
import com.readingagent.domain.ReadingProgress;
import com.readingagent.dto.ProgressDtos.ProgressResponse;
import com.readingagent.dto.ProgressDtos.SaveProgressRequest;
import com.readingagent.repository.ReadingProgressRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressService {
    private final ReadingProgressRepository progressRepository;
    private final BookService bookService;

    public ProgressService(ReadingProgressRepository progressRepository, BookService bookService) {
        this.progressRepository = progressRepository;
        this.bookService = bookService;
    }

    @Transactional
    public ProgressResponse save(Long bookId, SaveProgressRequest request) {
        Book book = bookService.getBook(bookId);
        ReadingProgress progress = progressRepository.findByBookIdAndUserKey(bookId, request.userKey())
                .orElseGet(ReadingProgress::new);
        progress.setBook(book);
        progress.setUserKey(request.userKey());
        progress.setChapterId(request.chapterId());
        progress.setScrollPercent(Math.max(0, Math.min(100, request.scrollPercent() == null ? 0 : request.scrollPercent())));
        progress.setUpdatedAt(Instant.now());
        return ProgressResponse.from(progressRepository.save(progress));
    }

    @Transactional(readOnly = true)
    public ProgressResponse get(Long bookId, String userKey) {
        return progressRepository.findByBookIdAndUserKey(bookId, userKey)
                .map(ProgressResponse::from)
                .orElse(null);
    }
}
