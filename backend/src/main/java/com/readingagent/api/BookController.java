package com.readingagent.api;

import com.readingagent.dto.BookDtos.BookSummary;
import com.readingagent.dto.BookDtos.ChapterDetail;
import com.readingagent.dto.BookDtos.ChapterSummary;
import com.readingagent.dto.BookDtos.UploadBookResponse;
import com.readingagent.service.BookService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadBookResponse upload(@RequestParam("file") @NotNull MultipartFile file,
                                     @RequestParam(value = "title", required = false) String title,
                                     @RequestParam(value = "author", required = false) String author) {
        return bookService.upload(file, title, author);
    }

    @GetMapping
    public List<BookSummary> list() {
        return bookService.listBooks();
    }

    @GetMapping("/{bookId}/chapters")
    public List<ChapterSummary> chapters(@PathVariable Long bookId) {
        return bookService.listChapters(bookId);
    }

    @GetMapping("/chapters/{chapterId}")
    public ChapterDetail chapter(@PathVariable Long chapterId) {
        return bookService.getChapter(chapterId);
    }

    @DeleteMapping("/{bookId}")
    public ResponseEntity<Void> delete(@PathVariable Long bookId) {
        bookService.deleteBook(bookId);
        return ResponseEntity.noContent().build();
    }
}
