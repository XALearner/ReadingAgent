package com.readingagent.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Chunker {
    @Value("${app.chunk-size}")
    private int chunkSize;

    @Value("${app.chunk-overlap}")
    private int overlap;

    public List<String> split(String content) {
        List<String> chunks = new ArrayList<>();
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return chunks;
        }
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }
}
