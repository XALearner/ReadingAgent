package com.readingagent.dto;

import com.readingagent.domain.Book;
import com.readingagent.domain.Chapter;
import java.time.Instant;

public final class BookDtos {
    private BookDtos() {
    }

    public record BookSummary(Long id, String title, String author, String fileName, Integer chapterCount,
                              Instant createdAt) {
        public static BookSummary from(Book book) {
            return new BookSummary(book.getId(), book.getTitle(), book.getAuthor(), book.getFileName(),
                    book.getChapterCount(), book.getCreatedAt());
        }
    }

    public record ChapterSummary(Long id, Integer sortOrder, String title) {
        public static ChapterSummary from(Chapter chapter) {
            return new ChapterSummary(chapter.getId(), chapter.getSortOrder(), chapter.getTitle());
        }
    }

    public record ChapterDetail(Long id, Long bookId, Integer sortOrder, String title, String content,
                                String contentHtml) {
        public static ChapterDetail from(Chapter chapter) {
            return new ChapterDetail(chapter.getId(), chapter.getBook().getId(), chapter.getSortOrder(),
                    chapter.getTitle(), chapter.getContent(), chapter.getContentHtml());
        }
    }

    public record UploadBookResponse(BookSummary book, ChapterDetail firstChapter) {
    }
}
