package com.readingagent.agent;

import com.readingagent.domain.Book;
import com.readingagent.dto.AiDtos.AgentStep;
import com.readingagent.dto.AiDtos.MultiAgentResponse;
import com.readingagent.dto.AiDtos.SourceSnippet;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MultiAgentCoordinator {
    private static final Logger log = LoggerFactory.getLogger(MultiAgentCoordinator.class);

    private final RetrievalAgent retrievalAgent;
    private final AnalysisAgent analysisAgent;
    private final ReviewAgent reviewAgent;

    public MultiAgentCoordinator(RetrievalAgent retrievalAgent, AnalysisAgent analysisAgent,
                                 ReviewAgent reviewAgent) {
        this.retrievalAgent = retrievalAgent;
        this.analysisAgent = analysisAgent;
        this.reviewAgent = reviewAgent;
    }

    public MultiAgentResponse analyze(Book book, String question) {
        List<AgentStep> steps = new ArrayList<>();
        List<SourceSnippet> sources = retrievalAgent.retrieve(book, question);
        steps.add(new AgentStep("retrieval", "completed", "已找到 " + sources.size() + " 个相关书籍片段"));

        if (sources.isEmpty()) {
            steps.add(new AgentStep("analysis", "skipped", "没有可用于分析的书籍证据"));
            steps.add(new AgentStep("review", "skipped", "没有分析草稿需要审校"));
            return new MultiAgentResponse(
                    "没有找到与问题相关的书籍内容。请先重建本书 RAG 索引，或换一种方式描述问题。",
                    sources,
                    steps);
        }

        String draft;
        try {
            draft = analysisAgent.analyze(book, question, sources);
            steps.add(new AgentStep("analysis", "completed", "已完成跨片段归纳并形成分析草稿"));
        } catch (RuntimeException ex) {
            log.warn("Analysis agent failed for book {}", book.getId(), ex);
            steps.add(new AgentStep("analysis", "failed", "分析 Agent 调用失败"));
            steps.add(new AgentStep("review", "skipped", "没有分析草稿需要审校"));
            return new MultiAgentResponse(
                    "深度分析暂时失败：请确认大模型配置和账号额度正常。",
                    sources,
                    steps);
        }

        try {
            String answer = reviewAgent.review(book, question, sources, draft);
            steps.add(new AgentStep("review", "completed", "已完成事实、引用和完整性审校"));
            return new MultiAgentResponse(answer, sources, steps);
        } catch (RuntimeException ex) {
            log.warn("Review agent failed for book {}, returning analysis draft", book.getId(), ex);
            steps.add(new AgentStep("review", "fallback", "审校调用失败，已返回分析 Agent 的草稿"));
            return new MultiAgentResponse(draft, sources, steps);
        }
    }
}
