package com.readingagent.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Component
public class BookParser {
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "(?m)^(第[一二三四五六七八九十百千万0-9]+[章节卷回].*|#{1,3}\\s+.+)$");
    private static final Safelist EPUB_HTML_SAFELIST = Safelist.relaxed()
            .addTags("article", "aside", "section", "figure", "figcaption", "main", "nav", "span", "div",
                    "h1", "h2", "h3", "h4", "h5", "h6")
            .addAttributes(":all", "class", "id", "title")
            .addAttributes("img", "src", "alt", "title", "width", "height")
            .addAttributes("a", "href", "title")
            .addProtocols("img", "src", "data")
            .addProtocols("a", "href", "http", "https", "#");

    public ParsedBook parse(MultipartFile file, String title, String author) {
        try {
            String originalFileName = file.getOriginalFilename();
            String fallbackTitle = stripExtension(originalFileName == null ? "未命名书籍" : originalFileName);
            String extension = extension(originalFileName);
            return switch (extension) {
                case "pdf" -> parsePdf(file, title, author, fallbackTitle);
                case "epub" -> parseEpub(file, title, author, fallbackTitle);
                case "txt", "md", "markdown", "" -> parseText(file, title, author, fallbackTitle);
                default -> throw new IllegalArgumentException("暂时只支持 TXT、Markdown、EPUB 或 PDF 文件");
            };
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) ex;
            }
            throw new IllegalArgumentException("文件解析失败: " + ex.getMessage(), ex);
        }
    }

    private ParsedBook parseText(MultipartFile file, String title, String author, String fallbackTitle) throws IOException {
        String content = normalizeText(new String(file.getBytes(), StandardCharsets.UTF_8));
        ensureContent(content);
            return new ParsedBook(blankToDefault(title, fallbackTitle), author, file.getOriginalFilename(), splitChapters(content));
    }

    private ParsedBook parsePdf(MultipartFile file, String title, String author, String fallbackTitle) throws IOException {
        List<ParsedChapter> chapters = new ArrayList<>();
        String pdfTitle = null;
        String pdfAuthor = null;
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            if (document.getDocumentInformation() != null) {
                pdfTitle = document.getDocumentInformation().getTitle();
                pdfAuthor = document.getDocumentInformation().getAuthor();
            }
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String content = normalizeText(stripper.getText(document));
                if (!content.isBlank()) {
                    chapters.add(new ParsedChapter("第 " + page + " 页", content));
                }
            }
        }
        ensureChapters(chapters);
        return new ParsedBook(blankToDefault(title, blankToDefault(pdfTitle, fallbackTitle)),
                blankToDefault(author, pdfAuthor), file.getOriginalFilename(), chapters);
    }

    private ParsedBook parseEpub(MultipartFile file, String title, String author, String fallbackTitle) throws Exception {
        Map<String, byte[]> entries = readZipEntries(file);
        String opfPath = findOpfPath(entries);
        Document opf = parseXml(entries.get(opfPath));
        String basePath = basePath(opfPath);
        Map<String, String> manifest = readManifest(opf, basePath);
        List<ParsedChapter> chapters = readEpubChapters(opf, manifest, entries);
        ensureChapters(chapters);
        String epubTitle = firstText(opf, "title");
        String epubAuthor = firstText(opf, "creator");
        return new ParsedBook(blankToDefault(title, blankToDefault(epubTitle, fallbackTitle)),
                blankToDefault(author, epubAuthor), file.getOriginalFilename(), chapters);
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

    private Map<String, byte[]> readZipEntries(MultipartFile file) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(normalizeZipPath(entry.getName()), zip.readAllBytes());
                }
            }
        }
        return entries;
    }

    private String findOpfPath(Map<String, byte[]> entries) throws Exception {
        byte[] container = entries.get("META-INF/container.xml");
        if (container == null) {
            throw new IllegalArgumentException("EPUB 缺少 META-INF/container.xml");
        }
        Document document = parseXml(container);
        NodeList rootFiles = document.getElementsByTagNameNS("*", "rootfile");
        if (rootFiles.getLength() == 0) {
            throw new IllegalArgumentException("EPUB 缺少 OPF 入口文件");
        }
        Element rootFile = (Element) rootFiles.item(0);
        return normalizeZipPath(rootFile.getAttribute("full-path"));
    }

    private Map<String, String> readManifest(Document opf, String basePath) {
        Map<String, String> manifest = new HashMap<>();
        NodeList items = opf.getElementsByTagNameNS("*", "item");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String id = item.getAttribute("id");
            String href = item.getAttribute("href");
            if (!id.isBlank() && !href.isBlank()) {
                manifest.put(id, resolveZipPath(basePath, href));
            }
        }
        return manifest;
    }

    private List<ParsedChapter> readEpubChapters(Document opf, Map<String, String> manifest,
                                                Map<String, byte[]> entries) {
        List<ParsedChapter> chapters = new ArrayList<>();
        NodeList itemRefs = opf.getElementsByTagNameNS("*", "itemref");
        for (int i = 0; i < itemRefs.getLength(); i++) {
            Element itemRef = (Element) itemRefs.item(i);
            String path = manifest.get(itemRef.getAttribute("idref"));
            byte[] bytes = path == null ? null : entries.get(path);
            if (bytes == null) {
                continue;
            }
            org.jsoup.nodes.Document html = Jsoup.parse(new String(bytes, StandardCharsets.UTF_8));
            html.select("script, iframe, object, embed, link, meta").remove();
            rewriteEpubImages(html, path, entries);
            String rawHtml = html.body() == null ? html.html() : html.body().html();
            String contentHtml = cleanEpubHtml(rawHtml);
            String content = normalizeText(html.body() == null ? html.text() : html.body().text());
            if (!content.isBlank()) {
                String chapterTitle = blankToDefault(html.title(), firstHeading(html));
                chapters.add(new ParsedChapter(blankToDefault(chapterTitle, "章节 " + (chapters.size() + 1)),
                        content, contentHtml));
            }
        }
        return chapters;
    }

    private void rewriteEpubImages(org.jsoup.nodes.Document html, String chapterPath, Map<String, byte[]> entries) {
        String chapterBasePath = basePath(chapterPath);
        for (org.jsoup.nodes.Element image : html.select("img[src]")) {
            String src = image.attr("src");
            if (src.isBlank() || src.startsWith("data:") || src.startsWith("http://") || src.startsWith("https://")) {
                continue;
            }
            String imagePath = resolveZipPath(chapterBasePath, src);
            byte[] imageBytes = entries.get(imagePath);
            if (imageBytes == null) {
                continue;
            }
            image.attr("src", "data:" + mimeType(imagePath) + ";base64," + Base64.getEncoder().encodeToString(imageBytes));
        }
    }

    private String cleanEpubHtml(String html) {
        org.jsoup.nodes.Document.OutputSettings settings = new org.jsoup.nodes.Document.OutputSettings()
                .prettyPrint(false);
        return Jsoup.clean(html, "", EPUB_HTML_SAFELIST, settings);
    }

    private Document parseXml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream input = new ByteArrayInputStream(bytes)) {
            return factory.newDocumentBuilder().parse(input);
        }
    }

    private String firstText(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent().trim();
    }

    private String firstHeading(org.jsoup.nodes.Document html) {
        org.jsoup.nodes.Element heading = html.selectFirst("h1, h2, h3");
        return heading == null ? null : heading.text();
    }

    private String extension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String normalizeText(String content) {
        return content.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f ]+", " ")
                .replaceAll("(?m)^\\s+", "")
                .trim();
    }

    private void ensureContent(String content) {
        if (content.isBlank()) {
            throw new IllegalArgumentException("文件内容为空");
        }
    }

    private void ensureChapters(List<ParsedChapter> chapters) {
        if (chapters.isEmpty()) {
            throw new IllegalArgumentException("没有提取到可阅读的正文内容");
        }
    }

    private String basePath(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash + 1);
    }

    private String resolveZipPath(String basePath, String href) {
        String cleanHref = href.split("[#?]", 2)[0];
        String decodedHref = URLDecoder.decode(cleanHref, StandardCharsets.UTF_8);
        return normalizeZipPath(Path.of(basePath).resolve(decodedHref).normalize().toString());
    }

    private String normalizeZipPath(String path) {
        return path.replace('\\', '/');
    }

    private String mimeType(String path) {
        return switch (extension(path)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }

    public record ParsedBook(String title, String author, String fileName, List<ParsedChapter> chapters) {
    }

    public record ParsedChapter(String title, String content, String contentHtml) {
        public ParsedChapter(String title, String content) {
            this(title, content, null);
        }
    }
}
