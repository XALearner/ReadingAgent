package com.readingagent.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class AiDtos {
    private AiDtos() {
    }

    public record AskRequest(@NotBlank String question, Long chapterId) {
    }

    public record SourceSnippet(Long bookId, Long chapterId, String chapterTitle, String content) {
    }

    public record AskResponse(String answer, List<SourceSnippet> sources) {
    }
}
