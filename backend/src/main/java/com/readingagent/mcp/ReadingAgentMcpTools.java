package com.readingagent.mcp;

import com.readingagent.dto.AiDtos.AskResponse;
import com.readingagent.dto.AiDtos.MultiAgentResponse;
import com.readingagent.dto.AiDtos.SourceSnippet;
import com.readingagent.dto.BookDtos.BookSummary;
import com.readingagent.dto.BookDtos.ChapterDetail;
import com.readingagent.dto.BookDtos.ChapterSummary;
import com.readingagent.dto.HighlightDtos.HighlightResponse;
import com.readingagent.service.BookService;
import com.readingagent.service.HighlightService;
import com.readingagent.service.RagService;
import com.readingagent.agent.MultiAgentCoordinator;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class ReadingAgentMcpTools {
    private static final int DEFAULT_CHAPTER_CHARACTERS = 8_000;
    private static final int MAX_CHAPTER_CHARACTERS = 20_000;

    private final BookService bookService;
    private final HighlightService highlightService;
    private final RagService ragService;
    private final MultiAgentCoordinator multiAgentCoordinator;

    public ReadingAgentMcpTools(BookService bookService, HighlightService highlightService, RagService ragService,
                                MultiAgentCoordinator multiAgentCoordinator) {
        this.bookService = bookService;
        this.highlightService = highlightService;
        this.ragService = ragService;
        this.multiAgentCoordinator = multiAgentCoordinator;
    }

    @Tool(name = "list_books", description = "列出 ReadingAgent 本地书架中的全部书籍及其 ID、作者和章节数量")
    public List<BookSummary> listBooks() {
        return bookService.listBooks();
    }

    @Tool(name = "get_book_chapters", description = "根据书籍 ID 获取按阅读顺序排列的章节目录")
    public List<ChapterSummary> getBookChapters(
            @ToolParam(description = "书籍 ID") Long bookId) {
        return bookService.listChapters(bookId);
    }

    @Tool(name = "read_chapter", description = "读取指定章节的一段纯文本；长章节可通过 offset 分段读取")
    public ChapterContent readChapter(
            @ToolParam(description = "章节 ID") Long chapterId,
            @ToolParam(description = "从第几个字符开始，默认 0", required = false) Integer offset,
            @ToolParam(description = "最多返回的字符数，默认 8000，最大 20000", required = false) Integer maxCharacters) {
        ChapterDetail chapter = bookService.getChapter(chapterId);
        String content = chapter.content() == null ? "" : chapter.content();
        int start = Math.max(0, Math.min(offset == null ? 0 : offset, content.length()));
        int limit = Math.max(1, Math.min(maxCharacters == null ? DEFAULT_CHAPTER_CHARACTERS : maxCharacters,
                MAX_CHAPTER_CHARACTERS));
        int end = Math.min(content.length(), start + limit);
        return new ChapterContent(chapter.id(), chapter.bookId(), chapter.sortOrder(), chapter.title(),
                content.substring(start, end), start, end, content.length(), end < content.length());
    }

    @Tool(name = "search_book", description = "在指定书籍中进行语义检索，返回最相关的章节片段和章节 ID")
    public List<SourceSnippet> searchBook(
            @ToolParam(description = "书籍 ID") Long bookId,
            @ToolParam(description = "要检索的问题或关键词") String query) {
        requireText(query, "检索内容不能为空");
        return ragService.search(bookService.getBook(bookId), query);
    }

    @Tool(name = "ask_book", description = "基于指定书籍的 RAG 索引调用大模型回答问题，并返回引用片段")
    public AskResponse askBook(
            @ToolParam(description = "书籍 ID") Long bookId,
            @ToolParam(description = "要询问的问题") String question) {
        requireText(question, "问题不能为空");
        return ragService.ask(bookService.getBook(bookId), null, question);
    }

    @Tool(name = "analyze_book", description = "使用检索、分析和审校 Multi Agent 工作流深度分析指定书籍中的复杂问题")
    public MultiAgentResponse analyzeBook(
            @ToolParam(description = "书籍 ID") Long bookId,
            @ToolParam(description = "需要跨章节分析、比较或论证的问题") String question) {
        requireText(question, "问题不能为空");
        return multiAgentCoordinator.analyze(bookService.getBook(bookId), question);
    }

    @Tool(name = "list_highlights", description = "列出指定书籍中的全部划线和笔记")
    public List<HighlightResponse> listHighlights(
            @ToolParam(description = "书籍 ID") Long bookId) {
        return highlightService.list(bookId);
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    public record ChapterContent(Long id, Long bookId, Integer sortOrder, String title, String content,
                                 int offset, int nextOffset, int totalCharacters, boolean hasMore) {
    }
}
