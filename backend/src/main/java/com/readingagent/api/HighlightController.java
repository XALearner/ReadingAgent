package com.readingagent.api;

import com.readingagent.dto.HighlightDtos.CreateHighlightRequest;
import com.readingagent.dto.HighlightDtos.HighlightResponse;
import com.readingagent.service.HighlightService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books/{bookId}/highlights")
public class HighlightController {
    private final HighlightService highlightService;

    public HighlightController(HighlightService highlightService) {
        this.highlightService = highlightService;
    }

    @PostMapping
    public HighlightResponse create(@PathVariable Long bookId, @Valid @RequestBody CreateHighlightRequest request) {
        return highlightService.create(bookId, request);
    }

    @GetMapping
    public List<HighlightResponse> list(@PathVariable Long bookId) {
        return highlightService.list(bookId);
    }
}
