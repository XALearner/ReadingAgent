package com.readingagent.agent;

import com.readingagent.domain.Book;
import com.readingagent.dto.AiDtos.SourceSnippet;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class ReviewAgent {
    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;

    public ReviewAgent(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        this.chatClientBuilderProvider = chatClientBuilderProvider;
    }

    public String review(Book book, String question, List<SourceSnippet> sources, String draft) {
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            throw new IllegalStateException("大模型尚未配置");
        }

        String result = builder.build()
                .prompt()
                .system("""
                        你是 ReadingAgent 的审校 Agent。请对分析草稿进行事实、引用和完整性检查。
                        删除或改写证据无法支持的断言，修正章节归属，补充遗漏的重要证据。
                        保留明确标注的合理推断，但不得把推断伪装成原文事实。
                        直接输出审校后的最终答案，不要输出审校过程或评分。
                        """)
                .user("""
                        书名：%s

                        用户问题：%s

                        书籍证据：
                        %s

                        待审校草稿：
                        %s
                        """.formatted(book.getTitle(), question, AgentContextFormatter.format(sources), draft))
                .call()
                .content();
        if (result == null || result.isBlank()) {
            throw new IllegalStateException("审校 Agent 未返回内容");
        }
        return result;
    }
}
