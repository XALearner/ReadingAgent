package com.readingagent.agent;

import com.readingagent.domain.Book;
import com.readingagent.dto.AiDtos.SourceSnippet;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class AnalysisAgent {
    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;

    public AnalysisAgent(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        this.chatClientBuilderProvider = chatClientBuilderProvider;
    }

    public String analyze(Book book, String question, List<SourceSnippet> sources) {
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            throw new IllegalStateException("大模型尚未配置");
        }

        String result = builder.build()
                .prompt()
                .system("""
                        你是 ReadingAgent 的分析 Agent。你的任务是基于给定书籍证据形成严谨的分析草稿。
                        先识别问题包含的子问题，再综合不同章节的信息回答。
                        只把证据明确支持的内容表述为书中结论；推断必须标注为推断。
                        在关键结论后标注对应章节名称。输出完整草稿，不要描述你的工作流程。
                        """)
                .user("""
                        书名：%s

                        用户问题：%s

                        书籍证据：
                        %s
                        """.formatted(book.getTitle(), question, AgentContextFormatter.format(sources)))
                .call()
                .content();
        if (result == null || result.isBlank()) {
            throw new IllegalStateException("分析 Agent 未返回内容");
        }
        return result;
    }
}
