package com.readingagent.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class AiDtos {
    private AiDtos() {
    }

    public record AskRequest(@NotBlank String question, Long chapterId, String userKey, String sessionId) {
    }

    public record SourceSnippet(Long bookId, Long chapterId, String chapterTitle, String content) {
    }

    public record AskResponse(String answer, List<SourceSnippet> sources, String sessionId) {
        public AskResponse(String answer, List<SourceSnippet> sources) {
            this(answer, sources, null);
        }
    }

    public record DeepAnalysisRequest(@NotBlank String question, String userKey, String sessionId) {
    }

    public record AgentStep(String agent, String status, String summary) {
    }

    public record MultiAgentResponse(String answer, List<SourceSnippet> sources, List<AgentStep> steps,
                                     String sessionId) {
        public MultiAgentResponse(String answer, List<SourceSnippet> sources, List<AgentStep> steps) {
            this(answer, sources, steps, null);
        }
    }
}
