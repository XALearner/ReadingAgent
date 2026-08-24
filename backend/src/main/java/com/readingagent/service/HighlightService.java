package com.readingagent.service;

import com.readingagent.domain.Book;
import com.readingagent.domain.Chapter;
import com.readingagent.domain.Highlight;
import com.readingagent.dto.HighlightDtos.CreateHighlightRequest;
import com.readingagent.dto.HighlightDtos.HighlightResponse;
import com.readingagent.repository.ChapterRepository;
import com.readingagent.repository.HighlightRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HighlightService {
    private final HighlightRepository highlightRepository;
    private final ChapterRepository chapterRepository;
    private final BookService bookService;

    public HighlightService(HighlightRepository highlightRepository, ChapterRepository chapterRepository,
                            BookService bookService) {
        this.highlightRepository = highlightRepository;
        this.chapterRepository = chapterRepository;
        this.bookService = bookService;
    }

    @Transactional
    public HighlightResponse create(Long bookId, CreateHighlightRequest request) {
        Book book = bookService.getBook(bookId);
        Chapter chapter = chapterRepository.findById(request.chapterId())
                .orElseThrow(() -> new IllegalArgumentException("章节不存在"));
        if (!chapter.getBook().getId().equals(bookId)) {
            throw new IllegalArgumentException("章节不属于当前书籍");
        }

        Highlight highlight = new Highlight();
        highlight.setBook(book);
        highlight.setChapter(chapter);
        highlight.setSelectedText(request.selectedText());
        highlight.setNote(request.note());
        return HighlightResponse.from(highlightRepository.save(highlight));
    }

    @Transactional(readOnly = true)
    public List<HighlightResponse> list(Long bookId) {
        bookService.getBook(bookId);
        return highlightRepository.findByBookIdOrderByCreatedAtDesc(bookId).stream()
                .map(HighlightResponse::from)
                .toList();
    }
}
