package com.readingagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.readingagent.domain.Book;
import com.readingagent.domain.AgentMemory;
import com.readingagent.repository.AgentMemoryRepository;
import com.readingagent.repository.AgentMessageRepository;
import com.readingagent.repository.AgentSessionRepository;
import com.readingagent.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AgentMemoryServiceTest {
    @Autowired private BookRepository books;
    @Autowired private AgentSessionRepository sessions;
    @Autowired private AgentMessageRepository messages;
    @Autowired private AgentMemoryRepository memories;

    @Test
    void persistsConversationAndScopesItToBookAndUser() {
        Book book = new Book();
        book.setTitle("测试书");
        book.setFileName("test.txt");
        Book savedBook = books.save(book);
        AgentMemoryService service = new AgentMemoryService(sessions, messages, memories, noModel());

        String sessionId = service.saveExchange(savedBook, "reader-1", null, "第一问", "第一答", "quick");
        service.saveExchange(savedBook, "reader-1", sessionId, "第二问", "第二答", "deep");

        AgentMemoryService reloaded = new AgentMemoryService(sessions, messages, memories, noModel());
        assertThat(reloaded.listMessages(savedBook.getId(), "reader-1", sessionId))
                .extracting(AgentMemoryService.MessageView::content)
                .containsExactly("第一问", "第一答", "第二问", "第二答");
        assertThat(reloaded.context(savedBook, "reader-1", sessionId).text()).contains("第一问", "第二答");
        assertThatThrownBy(() -> reloaded.listMessages(savedBook.getId(), "reader-2", sessionId))
                .isInstanceOf(IllegalArgumentException.class);

        AgentMemory memory = new AgentMemory();
        memory.setBook(savedBook);
        memory.setUserKey("reader-1");
        memory.setSummary("用户正在研究人物关系");
        memories.save(memory);
        String secondSession = reloaded.createSession(savedBook, "reader-1").getId();
        assertThat(reloaded.context(savedBook, "reader-1", secondSession).text())
                .contains("用户正在研究人物关系").doesNotContain("第一问");
        assertThat(reloaded.context(savedBook, "reader-2", null).text()).isEmpty();
        reloaded.clearMemory(savedBook.getId(), "reader-1");
        assertThat(reloaded.context(savedBook, "reader-1", secondSession).text()).isEmpty();

        reloaded.deleteBookData(savedBook.getId());
        assertThat(sessions.findById(sessionId)).isEmpty();
        assertThat(messages.findBySessionIdOrderByIdAsc(sessionId)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<ChatClient.Builder> noModel() {
        return mock(ObjectProvider.class);
    }
}
