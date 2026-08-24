package com.readingagent.dto;

import com.readingagent.domain.ReadingProgress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class ProgressDtos {
    private ProgressDtos() {
    }

    public record SaveProgressRequest(@NotBlank String userKey, @NotNull Long chapterId, Integer scrollPercent) {
    }

    public record ProgressResponse(Long bookId, String userKey, Long chapterId, Integer scrollPercent,
                                   Instant updatedAt) {
        public static ProgressResponse from(ReadingProgress progress) {
            return new ProgressResponse(progress.getBook().getId(), progress.getUserKey(), progress.getChapterId(),
                    progress.getScrollPercent(), progress.getUpdatedAt());
        }
    }
}
