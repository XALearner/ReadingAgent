package com.readingagent.agent;

import com.readingagent.domain.Book;
import com.readingagent.dto.AiDtos.SourceSnippet;
import com.readingagent.service.RagService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RetrievalAgent {
    private final RagService ragService;

    public RetrievalAgent(RagService ragService) {
        this.ragService = ragService;
    }

    public List<SourceSnippet> retrieve(Book book, String question) {
        return ragService.search(book, question);
    }
}
