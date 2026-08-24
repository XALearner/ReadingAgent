package com.readingagent.api;

import com.readingagent.dto.ProgressDtos.ProgressResponse;
import com.readingagent.dto.ProgressDtos.SaveProgressRequest;
import com.readingagent.service.ProgressService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books/{bookId}/progress")
public class ProgressController {
    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @PostMapping
    public ProgressResponse save(@PathVariable Long bookId, @Valid @RequestBody SaveProgressRequest request) {
        return progressService.save(bookId, request);
    }

    @GetMapping
    public ProgressResponse get(@PathVariable Long bookId, @RequestParam(defaultValue = "demo-user") String userKey) {
        return progressService.get(bookId, userKey);
    }
}
