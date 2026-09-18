package com.readingagent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.readingagent.domain.Book;
import com.readingagent.dto.AiDtos.MultiAgentResponse;
import com.readingagent.dto.AiDtos.SourceSnippet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MultiAgentCoordinatorTest {
    @Mock
    private RetrievalAgent retrievalAgent;
    @Mock
    private AnalysisAgent analysisAgent;
    @Mock
    private ReviewAgent reviewAgent;

    private MultiAgentCoordinator coordinator;
    private Book book;

    @BeforeEach
    void setUp() {
        coordinator = new MultiAgentCoordinator(retrievalAgent, analysisAgent, reviewAgent);
        book = new Book();
        book.setId(1L);
        book.setTitle("测试书籍");
    }

    @Test
    void orchestratesRetrievalAnalysisAndReview() {
        List<SourceSnippet> sources = List.of(new SourceSnippet(1L, 2L, "第一章", "证据内容"));
        when(retrievalAgent.retrieve(book, "问题")).thenReturn(sources);
        when(analysisAgent.analyze(book, "问题", sources)).thenReturn("分析草稿");
        when(reviewAgent.review(book, "问题", sources, "分析草稿")).thenReturn("最终答案");

        MultiAgentResponse response = coordinator.analyze(book, "问题");

        assertThat(response.answer()).isEqualTo("最终答案");
        assertThat(response.sources()).isEqualTo(sources);
        assertThat(response.steps()).extracting(step -> step.status())
                .containsExactly("completed", "completed", "completed");
    }

    @Test
    void skipsModelAgentsWhenRetrievalFindsNothing() {
        when(retrievalAgent.retrieve(book, "问题")).thenReturn(List.of());

        MultiAgentResponse response = coordinator.analyze(book, "问题");

        assertThat(response.sources()).isEmpty();
        assertThat(response.steps()).extracting(step -> step.status())
                .containsExactly("completed", "skipped", "skipped");
        verify(analysisAgent, never()).analyze(book, "问题", List.of());
    }

    @Test
    void returnsDraftWhenReviewAgentFails() {
        List<SourceSnippet> sources = List.of(new SourceSnippet(1L, 2L, "第一章", "证据内容"));
        when(retrievalAgent.retrieve(book, "问题")).thenReturn(sources);
        when(analysisAgent.analyze(book, "问题", sources)).thenReturn("分析草稿");
        when(reviewAgent.review(book, "问题", sources, "分析草稿"))
                .thenThrow(new IllegalStateException("review failed"));

        MultiAgentResponse response = coordinator.analyze(book, "问题");

        assertThat(response.answer()).isEqualTo("分析草稿");
        assertThat(response.steps().get(2).status()).isEqualTo("fallback");
    }
}
