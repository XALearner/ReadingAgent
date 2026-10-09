package com.readingagent.service;

import com.readingagent.domain.AgentMemory;
import com.readingagent.domain.AgentMessage;
import com.readingagent.domain.AgentSession;
import com.readingagent.domain.Book;
import com.readingagent.repository.AgentMemoryRepository;
import com.readingagent.repository.AgentMessageRepository;
import com.readingagent.repository.AgentSessionRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentMemoryService {
    private static final Logger log = LoggerFactory.getLogger(AgentMemoryService.class);
    private static final int RECENT_MESSAGES = 8;
    private final AgentSessionRepository sessions;
    private final AgentMessageRepository messages;
    private final AgentMemoryRepository memories;
    private final ObjectProvider<ChatClient.Builder> chatBuilders;

    public AgentMemoryService(AgentSessionRepository sessions, AgentMessageRepository messages,
                              AgentMemoryRepository memories, ObjectProvider<ChatClient.Builder> chatBuilders) {
        this.sessions = sessions;
        this.messages = messages;
        this.memories = memories;
        this.chatBuilders = chatBuilders;
    }

    @Transactional
    public AgentSession createSession(Book book, String userKey) {
        AgentSession session = new AgentSession();
        session.setBook(book);
        session.setUserKey(requireUserKey(userKey));
        return sessions.save(session);
    }

    @Transactional(readOnly = true)
    public List<SessionView> listSessions(Long bookId, String userKey) {
        return sessions.findByBookIdAndUserKeyOrderByCreatedAtDesc(bookId, requireUserKey(userKey))
                .stream().map(session -> new SessionView(session.getId(), session.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public List<MessageView> listMessages(Long bookId, String userKey, String sessionId) {
        requireSession(bookId, userKey, sessionId);
        return messages.findBySessionIdOrderByIdAsc(sessionId).stream()
                .map(message -> new MessageView(message.getId(), message.getRole(), message.getContent(), message.getMode()))
                .toList();
    }

    @Transactional(readOnly = true)
    public String getLongTermMemory(Long bookId, String userKey) {
        return memories.findByBookIdAndUserKey(bookId, requireUserKey(userKey))
                .map(AgentMemory::getSummary).orElse("");
    }

    @Transactional(readOnly = true)
    public Context context(Book book, String userKey, String sessionId) {
        AgentSession session = sessionId == null ? null : requireSession(book.getId(), userKey, sessionId);
        String summary = getLongTermMemory(book.getId(), userKey);
        List<AgentMessage> recent = session == null ? new ArrayList<>()
                : new ArrayList<>(messages.findBySessionIdOrderByIdDesc(sessionId, PageRequest.of(0, RECENT_MESSAGES)));
        Collections.reverse(recent);
        StringBuilder context = new StringBuilder();
        if (!summary.isBlank()) {
            context.append("长期记忆（用户过往对话摘要，并非书籍证据）：\n").append(summary).append("\n\n");
        }
        if (!recent.isEmpty()) {
            context.append("本次会话最近对话：\n");
            for (AgentMessage message : recent) {
                context.append(message.getRole()).append(": ")
                        .append(limit(message.getContent(), 1200)).append('\n');
            }
        }
        return new Context(session == null ? null : session.getId(), context.toString());
    }

    @Transactional
    public String saveExchange(Book book, String userKey, String sessionId, String question,
                               String answer, String mode) {
        AgentSession session = sessionId == null ? createSession(book, userKey)
                : requireSession(book.getId(), userKey, sessionId);
        saveMessage(session, "user", question, mode);
        saveMessage(session, "assistant", answer, mode);
        return session.getId();
    }

    public void updateLongTermMemory(Book book, String userKey, String question, String answer) {
        ChatClient.Builder builder = chatBuilders.getIfAvailable();
        if (builder == null) {
            return;
        }
        try {
            String previous = memories.findByBookIdAndUserKey(book.getId(), requireUserKey(userKey))
                    .map(AgentMemory::getSummary).orElse("");
            String summary = builder.build().prompt()
                    .system("""
                            维护一段不超过 1500 字的读书助手长期记忆。只保留用户明确表达的阅读目标、偏好、
                            已讨论的主题以及对后续提问有用的上下文。不要把助手的回答当作用户事实，
                            不要把推断当作书籍原文。用简洁中文输出更新后的摘要；没有值得记住的信息就保持原摘要。
                            """)
                    .user("原摘要：\n%s\n\n新问题：\n%s\n\n助手回答：\n%s"
                            .formatted(limit(previous, 1800), limit(question, 2000), limit(answer, 3000)))
                    .call().content();
            if (summary != null && !summary.isBlank()) {
                AgentMemory memory = memories.findByBookIdAndUserKey(book.getId(), userKey).orElseGet(() -> {
                    AgentMemory created = new AgentMemory();
                    created.setBook(book);
                    created.setUserKey(userKey);
                    return created;
                });
                memory.setSummary(limit(summary, 2000));
                memories.save(memory);
            }
        } catch (RuntimeException ex) {
            log.warn("Could not update long-term memory for book {}", book.getId(), ex);
        }
    }

    @Transactional
    public void deleteSession(Long bookId, String userKey, String sessionId) {
        AgentSession session = requireSession(bookId, userKey, sessionId);
        messages.deleteBySessionId(sessionId);
        sessions.delete(session);
    }

    @Transactional
    public void clearMemory(Long bookId, String userKey) {
        memories.findByBookIdAndUserKey(bookId, requireUserKey(userKey)).ifPresent(memories::delete);
    }

    @Transactional
    public void deleteBookData(Long bookId) {
        for (AgentSession session : sessions.findByBookId(bookId)) {
            messages.deleteBySessionId(session.getId());
        }
        sessions.deleteByBookId(bookId);
        memories.deleteByBookId(bookId);
    }

    private AgentSession requireSession(Long bookId, String userKey, String sessionId) {
        AgentSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!session.getBook().getId().equals(bookId) || !session.getUserKey().equals(requireUserKey(userKey))) {
            throw new IllegalArgumentException("会话不存在");
        }
        return session;
    }

    private void saveMessage(AgentSession session, String role, String content, String mode) {
        AgentMessage message = new AgentMessage();
        message.setSession(session);
        message.setRole(role);
        message.setContent(content);
        message.setMode(mode);
        messages.save(message);
    }

    private String requireUserKey(String userKey) {
        if (userKey == null || userKey.isBlank() || userKey.length() > 100) {
            throw new IllegalArgumentException("userKey 不能为空且不能超过 100 个字符");
        }
        return userKey;
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record Context(String sessionId, String text) { }
    public record SessionView(String id, java.time.Instant createdAt) { }
    public record MessageView(Long id, String role, String content, String mode) { }
}
