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

    public record DeepAnalysisRequest(@NotBlank String question) {
    }

    public record AgentStep(String agent, String status, String summary) {
    }

    public record MultiAgentResponse(String answer, List<SourceSnippet> sources, List<AgentStep> steps) {
    }
}
