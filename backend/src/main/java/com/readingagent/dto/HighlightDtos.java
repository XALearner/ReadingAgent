package com.readingagent.dto;

import com.readingagent.domain.Highlight;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class HighlightDtos {
    private HighlightDtos() {
    }

    public record CreateHighlightRequest(@NotNull Long chapterId, @NotBlank String selectedText, String note) {
    }

    public record HighlightResponse(Long id, Long bookId, Long chapterId, String selectedText, String note,
                                    Instant createdAt) {
        public static HighlightResponse from(Highlight highlight) {
            return new HighlightResponse(highlight.getId(), highlight.getBook().getId(), highlight.getChapter().getId(),
                    highlight.getSelectedText(), highlight.getNote(), highlight.getCreatedAt());
        }
    }
}
