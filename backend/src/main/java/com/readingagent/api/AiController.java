package com.readingagent.api;

import com.readingagent.domain.Book;
import com.readingagent.agent.MultiAgentCoordinator;
import com.readingagent.dto.AiDtos.AskRequest;
import com.readingagent.dto.AiDtos.AskResponse;
import com.readingagent.dto.AiDtos.DeepAnalysisRequest;
import com.readingagent.dto.AiDtos.MultiAgentResponse;
import com.readingagent.service.BookService;
import com.readingagent.service.RagService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books/{bookId}/ai")
public class AiController {
    private final BookService bookService;
    private final RagService ragService;
    private final MultiAgentCoordinator multiAgentCoordinator;

    public AiController(BookService bookService, RagService ragService,
                        MultiAgentCoordinator multiAgentCoordinator) {
        this.bookService = bookService;
        this.ragService = ragService;
        this.multiAgentCoordinator = multiAgentCoordinator;
    }

    @PostMapping("/ask")
    public AskResponse ask(@PathVariable Long bookId, @Valid @RequestBody AskRequest request) {
        Book book = bookService.getBook(bookId);
        return ragService.ask(book, request.chapterId(), request.question());
    }

    @PostMapping("/analyze")
    public MultiAgentResponse analyze(@PathVariable Long bookId,
                                      @Valid @RequestBody DeepAnalysisRequest request) {
        Book book = bookService.getBook(bookId);
        return multiAgentCoordinator.analyze(book, request.question());
    }

    @PostMapping("/reindex")
    public ResponseEntity<Void> reindex(@PathVariable Long bookId) {
        bookService.reindexBook(bookId);
        return ResponseEntity.accepted().build();
    }
}
