package com.readingagent.agent;

import com.readingagent.dto.AiDtos.SourceSnippet;
import java.util.List;

final class AgentContextFormatter {
    private AgentContextFormatter() {
    }

    static String format(List<SourceSnippet> sources) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            SourceSnippet source = sources.get(i);
            context.append("证据 ").append(i + 1)
                    .append("，章节：").append(source.chapterTitle())
                    .append("，chapterId：").append(source.chapterId())
                    .append('\n').append(source.content()).append("\n\n");
        }
        return context.toString();
    }
}
