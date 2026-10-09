package com.readingagent.api;

import com.readingagent.domain.Book;
import com.readingagent.agent.MultiAgentCoordinator;
import com.readingagent.dto.AiDtos.AskRequest;
import com.readingagent.dto.AiDtos.AskResponse;
import com.readingagent.dto.AiDtos.DeepAnalysisRequest;
import com.readingagent.dto.AiDtos.MultiAgentResponse;
import com.readingagent.service.BookService;
import com.readingagent.service.AgentMemoryService;
import com.readingagent.service.RagService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books/{bookId}/ai")
public class AiController {
    private final BookService bookService;
    private final RagService ragService;
    private final MultiAgentCoordinator multiAgentCoordinator;
    private final AgentMemoryService memoryService;

    public AiController(BookService bookService, RagService ragService,
                        MultiAgentCoordinator multiAgentCoordinator, AgentMemoryService memoryService) {
        this.bookService = bookService;
        this.ragService = ragService;
        this.multiAgentCoordinator = multiAgentCoordinator;
        this.memoryService = memoryService;
    }

    @PostMapping("/ask")
    public AskResponse ask(@PathVariable Long bookId, @Valid @RequestBody AskRequest request) {
        Book book = bookService.getBook(bookId);
        String userKey = userKey(request.userKey());
        AgentMemoryService.Context context = memoryService.context(book, userKey, request.sessionId());
        AskResponse response = ragService.ask(book, request.chapterId(), request.question(), context.text());
        String sessionId = memoryService.saveExchange(book, userKey, context.sessionId(),
                request.question(), response.answer(), "quick");
        if (!response.sources().isEmpty()) {
            memoryService.updateLongTermMemory(book, userKey, request.question(), response.answer());
        }
        return new AskResponse(response.answer(), response.sources(), sessionId);
    }

    @PostMapping("/analyze")
    public MultiAgentResponse analyze(@PathVariable Long bookId,
                                      @Valid @RequestBody DeepAnalysisRequest request) {
        Book book = bookService.getBook(bookId);
        String userKey = userKey(request.userKey());
        AgentMemoryService.Context context = memoryService.context(book, userKey, request.sessionId());
        MultiAgentResponse response = multiAgentCoordinator.analyze(book, request.question(), context.text());
        String sessionId = memoryService.saveExchange(book, userKey, context.sessionId(),
                request.question(), response.answer(), "deep");
        if (!response.sources().isEmpty()) {
            memoryService.updateLongTermMemory(book, userKey, request.question(), response.answer());
        }
        return new MultiAgentResponse(response.answer(), response.sources(), response.steps(), sessionId);
    }

    @GetMapping("/sessions")
    public List<AgentMemoryService.SessionView> sessions(@PathVariable Long bookId,
                                                          @RequestParam(defaultValue = "demo-user") String userKey) {
        bookService.getBook(bookId);
        return memoryService.listSessions(bookId, userKey);
    }

    @PostMapping("/sessions")
    public AgentMemoryService.SessionView createSession(@PathVariable Long bookId,
                                                         @RequestParam(defaultValue = "demo-user") String userKey) {
        var session = memoryService.createSession(bookService.getBook(bookId), userKey);
        return new AgentMemoryService.SessionView(session.getId(), session.getCreatedAt());
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<AgentMemoryService.MessageView> messages(@PathVariable Long bookId,
                                                          @PathVariable String sessionId,
                                                          @RequestParam(defaultValue = "demo-user") String userKey) {
        bookService.getBook(bookId);
        return memoryService.listMessages(bookId, userKey, sessionId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable Long bookId, @PathVariable String sessionId,
                                               @RequestParam(defaultValue = "demo-user") String userKey) {
        bookService.getBook(bookId);
        memoryService.deleteSession(bookId, userKey, sessionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/memory")
    public ResponseEntity<Void> clearMemory(@PathVariable Long bookId,
                                             @RequestParam(defaultValue = "demo-user") String userKey) {
        bookService.getBook(bookId);
        memoryService.clearMemory(bookId, userKey);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/memory")
    public Map<String, String> memory(@PathVariable Long bookId,
                                       @RequestParam(defaultValue = "demo-user") String userKey) {
        bookService.getBook(bookId);
        return Map.of("summary", memoryService.getLongTermMemory(bookId, userKey));
    }

    private String userKey(String value) {
        return value == null ? "demo-user" : value;
    }

    @PostMapping("/reindex")
    public ResponseEntity<Void> reindex(@PathVariable Long bookId) {
        bookService.reindexBook(bookId);
        return ResponseEntity.accepted().build();
    }
}
