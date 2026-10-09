package com.readingagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.readingagent.domain.Book;
import com.readingagent.repository.ChapterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;

class RagServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void reportsUnavailableModelWhenChatBuilderCannotBeCreated() {
        ObjectProvider<ChatClient.Builder> chatBuilders = mock(ObjectProvider.class);
        ObjectProvider<VectorStore> vectorStores = mock(ObjectProvider.class);
        when(chatBuilders.getIfAvailable()).thenThrow(new BeanCreationException("chatClientBuilder"));
        RagService service = new RagService(vectorStores, chatBuilders,
                mock(Chunker.class), mock(ChapterRepository.class));
        Book book = new Book();
        book.setId(1L);

        assertThat(service.ask(book, null, "问题").answer()).contains("AI 问答还没有配置完成");
    }
}
