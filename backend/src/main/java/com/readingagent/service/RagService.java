package com.readingagent.service;

import com.readingagent.domain.Book;
import com.readingagent.domain.Chapter;
import com.readingagent.dto.AiDtos.AskResponse;
import com.readingagent.dto.AiDtos.SourceSnippet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RagService {
    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final Chunker chunker;

    public RagService(ObjectProvider<VectorStore> vectorStoreProvider,
                      ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                      Chunker chunker) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.chunker = chunker;
    }

    @Async
    public void indexBookAsync(Book book, List<Chapter> chapters) {
        try {
            indexBook(book, chapters);
        } catch (RuntimeException ex) {
            log.warn("Book {} uploaded, but AI index creation failed", book.getId(), ex);
        }
    }

    private void indexBook(Book book, List<Chapter> chapters) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            return;
        }
        List<Document> documents = new ArrayList<>();
        for (Chapter chapter : chapters) {
            List<String> chunks = chunker.split(chapter.getContent());
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("bookId", book.getId());
                metadata.put("bookTitle", book.getTitle());
                metadata.put("chapterId", chapter.getId());
                metadata.put("chapterTitle", chapter.getTitle());
                metadata.put("chunkIndex", i);
                documents.add(Document.builder()
                        .text(chunks.get(i))
                        .metadata(metadata)
                        .build());
            }
        }
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
    }

    public AskResponse ask(Book book, String question) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        ChatClient.Builder chatBuilder = chatClientBuilderProvider.getIfAvailable();
        if (vectorStore == null || chatBuilder == null) {
            return new AskResponse("AI 问答还没有配置完成：请确认 DASHSCOPE_API_KEY 和 Elasticsearch 已启动。", List.of());
        }

        List<Document> matched;
        try {
            matched = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(question)
                    .topK(12)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("RAG retrieval failed for book {}", book.getId(), ex);
            return new AskResponse("RAG 检索失败：请确认 Elasticsearch 正常、DashScope API Key 可用，并且 embedding 模型配置正确。", List.of());
        }

        List<Document> bookDocs = matched.stream()
                .filter(doc -> Objects.equals(String.valueOf(book.getId()), String.valueOf(doc.getMetadata().get("bookId"))))
                .limit(6)
                .toList();

        if (bookDocs.isEmpty()) {
            return new AskResponse("我没有在这本书里找到足够相关的内容。你可以换个问法，或者先确认这本书已经完成索引。", List.of());
        }

        StringBuilder context = new StringBuilder();
        List<SourceSnippet> sources = new ArrayList<>();
        for (int i = 0; i < bookDocs.size(); i++) {
            Document doc = bookDocs.get(i);
            Long chapterId = Long.valueOf(String.valueOf(doc.getMetadata().get("chapterId")));
            String chapterTitle = String.valueOf(doc.getMetadata().get("chapterTitle"));
            context.append("资料 ").append(i + 1)
                    .append("，章节：").append(chapterTitle)
                    .append("\n").append(doc.getText()).append("\n\n");
            sources.add(new SourceSnippet(book.getId(), chapterId, chapterTitle, doc.getText()));
        }

        String answer;
        try {
            answer = chatBuilder.build()
                    .prompt()
                    .system("""
                            你是一个读书助手。只根据给定的书籍资料回答问题。
                            如果资料不足，直接说明不足，不要编造。
                            回答要清晰、简洁，并尽量指出依据来自哪个章节。
                            """)
                    .user("""
                            书名：%s

                            资料：
                            %s

                            用户问题：%s
                            """.formatted(book.getTitle(), context, question))
                    .call()
                    .content();
        } catch (RuntimeException ex) {
            log.warn("RAG chat completion failed for book {}", book.getId(), ex);
            return new AskResponse("大模型调用失败：请确认 DASHSCOPE_API_KEY、QWEN_BASE_URL 和 QWEN_MODEL 配置正确，账号额度也仍然可用。", sources);
        }

        return new AskResponse(answer, sources);
    }
}
