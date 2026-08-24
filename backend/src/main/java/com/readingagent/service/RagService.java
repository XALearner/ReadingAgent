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
import org.springframework.stereotype.Service;

@Service
public class RagService {
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

    public void indexBook(Book book, List<Chapter> chapters) {
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

        List<Document> matched = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(12)
                .build());

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

        String answer = chatBuilder.build()
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

        return new AskResponse(answer, sources);
    }
}
