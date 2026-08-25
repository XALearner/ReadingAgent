package com.readingagent.service;

import com.readingagent.domain.Book;
import com.readingagent.domain.Chapter;
import com.readingagent.dto.BookDtos.BookSummary;
import com.readingagent.dto.BookDtos.ChapterDetail;
import com.readingagent.dto.BookDtos.ChapterSummary;
import com.readingagent.dto.BookDtos.UploadBookResponse;
import com.readingagent.repository.BookRepository;
import com.readingagent.repository.ChapterRepository;
import com.readingagent.repository.HighlightRepository;
import com.readingagent.repository.ReadingProgressRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BookService {
    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final HighlightRepository highlightRepository;
    private final ReadingProgressRepository readingProgressRepository;
    private final BookParser bookParser;
    private final RagService ragService;

    public BookService(BookRepository bookRepository, ChapterRepository chapterRepository,
                       HighlightRepository highlightRepository, ReadingProgressRepository readingProgressRepository,
                       BookParser bookParser,
                       RagService ragService) {
        this.bookRepository = bookRepository;
        this.chapterRepository = chapterRepository;
        this.highlightRepository = highlightRepository;
        this.readingProgressRepository = readingProgressRepository;
        this.bookParser = bookParser;
        this.ragService = ragService;
    }

    @Transactional
    public UploadBookResponse upload(MultipartFile file, String title, String author) {
        BookParser.ParsedBook parsed = bookParser.parse(file, title, author);

        Book book = new Book();
        book.setTitle(parsed.title());
        book.setAuthor(parsed.author());
        book.setFileName(parsed.fileName() == null ? parsed.title() : parsed.fileName());
        book.setChapterCount(parsed.chapters().size());
        book = bookRepository.save(book);

        List<Chapter> chapters = new ArrayList<>();
        for (int i = 0; i < parsed.chapters().size(); i++) {
            BookParser.ParsedChapter parsedChapter = parsed.chapters().get(i);
            Chapter chapter = new Chapter();
            chapter.setBook(book);
            chapter.setSortOrder(i + 1);
            chapter.setTitle(parsedChapter.title());
            chapter.setContent(parsedChapter.content());
            chapter.setContentHtml(parsedChapter.contentHtml());
            chapters.add(chapter);
        }
        chapters = chapterRepository.saveAll(chapters);
        ragService.indexBookAsync(book, chapters);

        return new UploadBookResponse(BookSummary.from(book), ChapterDetail.from(chapters.get(0)));
    }

    @Transactional(readOnly = true)
    public List<BookSummary> listBooks() {
        return bookRepository.findAll().stream().map(BookSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public Book getBook(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("书籍不存在"));
    }

    @Transactional(readOnly = true)
    public List<ChapterSummary> listChapters(Long bookId) {
        getBook(bookId);
        return chapterRepository.findByBookIdOrderBySortOrderAsc(bookId).stream()
                .map(ChapterSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChapterDetail getChapter(Long chapterId) {
        return ChapterDetail.from(chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("章节不存在")));
    }

    @Transactional
    public void deleteBook(Long bookId) {
        Book book = getBook(bookId);
        readingProgressRepository.deleteByBookId(bookId);
        highlightRepository.deleteByBookId(bookId);
        chapterRepository.deleteByBookId(bookId);
        bookRepository.delete(book);
    }
}
