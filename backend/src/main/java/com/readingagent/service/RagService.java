package com.readingagent.service;

import com.readingagent.domain.Book;
import com.readingagent.domain.Chapter;
import com.readingagent.dto.AiDtos.AskResponse;
import com.readingagent.dto.AiDtos.SourceSnippet;
import com.readingagent.repository.ChapterRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
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
    private static final int VECTOR_TOP_K = 30;
    private static final int CONTEXT_LIMIT = 6;
    private static final int INDEX_BATCH_SIZE = 64;
    private static final Pattern TERM_SPLITTER = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fff]+");

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final Chunker chunker;
    private final ChapterRepository chapterRepository;

    public RagService(ObjectProvider<VectorStore> vectorStoreProvider,
                      ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                      Chunker chunker,
                      ChapterRepository chapterRepository) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.chunker = chunker;
        this.chapterRepository = chapterRepository;
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
            log.info("Skip AI index for book {} because VectorStore is not configured", book.getId());
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
            log.info("Creating AI index for book {} with {} chunks", book.getId(), documents.size());
            for (int from = 0; from < documents.size(); from += INDEX_BATCH_SIZE) {
                int to = Math.min(from + INDEX_BATCH_SIZE, documents.size());
                vectorStore.add(documents.subList(from, to));
            }
            log.info("Created AI index for book {}", book.getId());
        }
    }

    public AskResponse ask(Book book, Long chapterId, String question) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        ChatClient.Builder chatBuilder = chatClientBuilderProvider.getIfAvailable();
        if (chatBuilder == null) {
            return new AskResponse("AI 问答还没有配置完成：请确认 DASHSCOPE_API_KEY 和 Elasticsearch 已启动。", List.of());
        }

        List<SourceSnippet> sources = vectorSources(book, question, vectorStore);
        if (sources.isEmpty()) {
            sources = fallbackSources(book, chapterId, question);
        }

        if (sources.isEmpty()) {
            return new AskResponse("我还没有在这本书里找到可用内容。请先点击“重建本书 RAG 索引”，或确认当前章节包含可阅读文本。", List.of());
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            SourceSnippet source = sources.get(i);
            context.append("资料 ").append(i + 1)
                    .append("，章节：").append(source.chapterTitle())
                    .append("\n").append(source.content()).append("\n\n");
        }

        String answer;
        try {
            answer = chatBuilder.build()
                    .prompt()
                    .system("""
                            你是一个读书助手。回答问题时优先参考给定的书籍资料。
                            如果书籍资料足够，请主要依据书籍资料回答，并尽量指出依据来自哪个章节。
                            如果书籍资料不足，可以结合你的通用知识补充回答，但要明确说明哪些内容来自书籍资料，哪些是补充推断或背景知识。
                            不要把书中没有的信息伪装成书里的内容。
                            回答要清晰、简洁。
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

    private List<SourceSnippet> vectorSources(Book book, String question, VectorStore vectorStore) {
        if (vectorStore == null) {
            return List.of();
        }
        List<Document> matched;
        try {
            matched = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(question)
                    .topK(VECTOR_TOP_K)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("RAG retrieval failed for book {}", book.getId(), ex);
            return List.of();
        }

        return matched.stream()
                .filter(doc -> Objects.equals(String.valueOf(book.getId()), String.valueOf(doc.getMetadata().get("bookId"))))
                .limit(CONTEXT_LIMIT)
                .map(doc -> new SourceSnippet(
                        book.getId(),
                        asLong(doc.getMetadata().get("chapterId")),
                        String.valueOf(doc.getMetadata().get("chapterTitle")),
                        doc.getText()))
                .toList();
    }

    private List<SourceSnippet> fallbackSources(Book book, Long activeChapterId, String question) {
        List<Chapter> chapters = chapterRepository.findByBookIdOrderBySortOrderAsc(book.getId());
        if (chapters.isEmpty()) {
            return List.of();
        }

        List<String> terms = queryTerms(question);
        Map<String, RankedSnippet> ranked = new LinkedHashMap<>();

        if (activeChapterId != null) {
            chapters.stream()
                    .filter(chapter -> Objects.equals(chapter.getId(), activeChapterId))
                    .findFirst()
                    .ifPresent(chapter -> addChapterSnippets(ranked, book, chapter, terms, true));
        }

        for (Chapter chapter : chapters) {
            addChapterSnippets(ranked, book, chapter, terms, false);
        }

        return ranked.values().stream()
                .sorted(Comparator.comparingInt(RankedSnippet::score).reversed())
                .limit(CONTEXT_LIMIT)
                .map(RankedSnippet::source)
                .toList();
    }

    private void addChapterSnippets(Map<String, RankedSnippet> ranked,
                                    Book book,
                                    Chapter chapter,
                                    List<String> terms,
                                    boolean activeChapter) {
        List<String> chunks = chunker.split(chapter.getContent());
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            int score = score(chunk, terms);
            if (score <= 0 && !activeChapter) {
                continue;
            }
            int adjustedScore = score + (activeChapter ? 2 : 0);
            String key = chapter.getId() + ":" + i;
            ranked.putIfAbsent(key, new RankedSnippet(adjustedScore, new SourceSnippet(
                    book.getId(),
                    chapter.getId(),
                    chapter.getTitle(),
                    excerpt(chunk))));
        }
    }

    private List<String> queryTerms(String question) {
        String normalized = question == null ? "" : question.toLowerCase();
        List<String> terms = new ArrayList<>();
        for (String term : TERM_SPLITTER.split(normalized)) {
            if (term.length() >= 2) {
                terms.add(term);
            }
        }
        normalized.codePoints()
                .filter(codePoint -> codePoint >= 0x4e00 && codePoint <= 0x9fff)
                .mapToObj(Character::toString)
                .forEach(terms::add);
        return terms;
    }

    private int score(String text, List<String> terms) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String normalized = text.toLowerCase();
        int score = 0;
        for (String term : terms) {
            int index = normalized.indexOf(term);
            while (index >= 0) {
                score++;
                index = normalized.indexOf(term, index + term.length());
            }
        }
        return score;
    }

    private String excerpt(String text) {
        if (text == null || text.length() <= 1400) {
            return text;
        }
        return text.substring(0, 1400);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record RankedSnippet(int score, SourceSnippet source) {
    }
}
