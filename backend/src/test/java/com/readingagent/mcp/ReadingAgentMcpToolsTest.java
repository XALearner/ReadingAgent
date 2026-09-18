package com.readingagent.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.readingagent.dto.BookDtos.ChapterDetail;
import com.readingagent.service.BookService;
import com.readingagent.service.HighlightService;
import com.readingagent.service.RagService;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallbackProvider;

@ExtendWith(MockitoExtension.class)
class ReadingAgentMcpToolsTest {
    @Mock
    private BookService bookService;
    @Mock
    private HighlightService highlightService;
    @Mock
    private RagService ragService;

    private ReadingAgentMcpTools tools;

    @BeforeEach
    void setUp() {
        tools = new ReadingAgentMcpTools(bookService, highlightService, ragService);
    }

    @Test
    void registersExpectedReadOnlyTools() {
        ToolCallbackProvider provider = new McpToolConfiguration().readingAgentToolCallbackProvider(tools);

        Set<String> names = Arrays.stream(provider.getToolCallbacks())
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertThat(names).containsExactlyInAnyOrder(
                "list_books",
                "get_book_chapters",
                "read_chapter",
                "search_book",
                "ask_book",
                "list_highlights");
    }

    @Test
    void readsLongChaptersInBoundedSegments() {
        when(bookService.getChapter(7L)).thenReturn(
                new ChapterDetail(7L, 2L, 3, "测试章节", "abcdefghij", "<p>abcdefghij</p>"));

        ReadingAgentMcpTools.ChapterContent result = tools.readChapter(7L, 3, 4);

        assertThat(result.content()).isEqualTo("defg");
        assertThat(result.offset()).isEqualTo(3);
        assertThat(result.nextOffset()).isEqualTo(7);
        assertThat(result.totalCharacters()).isEqualTo(10);
        assertThat(result.hasMore()).isTrue();
    }
}
