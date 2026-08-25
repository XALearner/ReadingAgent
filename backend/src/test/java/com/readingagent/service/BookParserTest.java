package com.readingagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class BookParserTest {
    private final BookParser parser = new BookParser();

    @Test
    void parsesPdfTextByPage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.pdf", "application/pdf", samplePdf());

        BookParser.ParsedBook book = parser.parse(file, null, null);

        assertThat(book.title()).isEqualTo("sample");
        assertThat(book.chapters()).hasSize(1);
        assertThat(book.chapters().get(0).title()).isEqualTo("第 1 页");
        assertThat(book.chapters().get(0).content()).contains("Hello PDF chapter");
    }

    @Test
    void parsesEpubSpineChapters() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.epub", "application/epub+zip", sampleEpub());

        BookParser.ParsedBook book = parser.parse(file, null, null);

        assertThat(book.title()).isEqualTo("EPUB Title");
        assertThat(book.author()).isEqualTo("EPUB Author");
        assertThat(book.chapters()).hasSize(2);
        assertThat(book.chapters().get(0).title()).isEqualTo("封面");
        assertThat(book.chapters().get(0).content()).isBlank();
        assertThat(book.chapters().get(0).contentHtml()).contains("data:image/png;base64,");
        assertThat(book.chapters().get(1).title()).isEqualTo("Chapter One");
        assertThat(book.chapters().get(1).content()).contains("Hello EPUB chapter");
        assertThat(book.chapters().get(1).contentHtml()).contains("Hello EPUB chapter", "data:image/png;base64,");
    }

    @Test
    void parsesEpubSvgCoverWithoutText() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.epub", "application/epub+zip", sampleEpub());

        BookParser.ParsedBook book = parser.parse(file, null, null);

        assertThat(book.chapters()).hasSize(2);
        assertThat(book.chapters().get(0).title()).isEqualTo("封面");
        assertThat(book.chapters().get(0).content()).isBlank();
        assertThat(book.chapters().get(0).contentHtml()).contains("data:image/png;base64,");
    }

    private byte[] samplePdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(72, 720);
                contentStream.showText("Hello PDF chapter");
                contentStream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] sampleEpub() throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            addZipEntry(zip, "META-INF/container.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                    """);
            addZipEntry(zip, "OEBPS/content.opf", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package version="3.0" xmlns="http://www.idpf.org/2007/opf"
                             xmlns:dc="http://purl.org/dc/elements/1.1/">
                      <metadata>
                        <dc:title>EPUB Title</dc:title>
                        <dc:creator>EPUB Author</dc:creator>
                      </metadata>
                      <manifest>
                        <item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/>
                        <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="cover"/>
                        <itemref idref="chapter1"/>
                      </spine>
                    </package>
                    """);
            addZipEntry(zip, "OEBPS/cover.xhtml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>封面</title></head>
                      <body>
                        <figure class="cover">
                          <svg xmlns="http://www.w3.org/2000/svg" version="1.1"
                               xmlns:xlink="http://www.w3.org/1999/xlink"
                               width="100%" height="100%" viewBox="0 0 812 1200">
                            <image width="812" height="1200" xlink:href="images/cover.png" />
                          </svg>
                        </figure>
                      </body>
                    </html>
                    """);
            addZipEntry(zip, "OEBPS/chapter1.xhtml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>Chapter One</title></head>
                      <body>
                        <h1>Chapter One</h1>
                        <p>Hello EPUB chapter</p>
                        <figure><img src="images/cover.png" alt="Cover"/><figcaption>Cover image</figcaption></figure>
                      </body>
                    </html>
                    """);
            addZipEntry(zip, "OEBPS/images/cover.png", Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="));
            zip.finish();
            return output.toByteArray();
        }
    }

    private void addZipEntry(ZipOutputStream zip, String path, String content) throws Exception {
        addZipEntry(zip, path, content.getBytes(StandardCharsets.UTF_8));
    }

    private void addZipEntry(ZipOutputStream zip, String path, byte[] content) throws Exception {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content);
        zip.closeEntry();
    }
}
