package com.readingagent.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class BookParser {
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "(?m)^(第[一二三四五六七八九十百千万0-9]+[章节卷回].*|#{1,3}\\s+.+)$");

    public ParsedBook parse(MultipartFile file, String title, String author) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .trim();
            if (content.isBlank()) {
                throw new IllegalArgumentException("文件内容为空");
            }
            String fallbackTitle = stripExtension(file.getOriginalFilename() == null ? "未命名书籍" : file.getOriginalFilename());
            return new ParsedBook(blankToDefault(title, fallbackTitle), author, file.getOriginalFilename(), splitChapters(content));
        } catch (Exception ex) {
            throw new IllegalArgumentException("暂时只支持 UTF-8 文本或 Markdown 文件: " + ex.getMessage(), ex);
        }
    }

    private List<ParsedChapter> splitChapters(String content) {
        Matcher matcher = CHAPTER_PATTERN.matcher(content);
        List<ParsedChapter> chapters = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<String> titles = new ArrayList<>();

        while (matcher.find()) {
            starts.add(matcher.start());
            titles.add(matcher.group().replaceFirst("^#{1,3}\\s+", "").trim());
        }

        if (starts.isEmpty()) {
            chapters.add(new ParsedChapter("正文", content));
            return chapters;
        }

        if (starts.get(0) > 0) {
            String preface = content.substring(0, starts.get(0)).trim();
            if (!preface.isBlank()) {
                chapters.add(new ParsedChapter("前言", preface));
            }
        }

        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = i + 1 < starts.size() ? starts.get(i + 1) : content.length();
            String chapterContent = content.substring(start, end).trim();
            chapters.add(new ParsedChapter(titles.get(i), chapterContent));
        }
        return chapters;
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record ParsedBook(String title, String author, String fileName, List<ParsedChapter> chapters) {
    }

    public record ParsedChapter(String title, String content) {
    }
}
