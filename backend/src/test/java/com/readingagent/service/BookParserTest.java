package com.readingagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
        assertThat(book.chapters()).hasSize(1);
        assertThat(book.chapters().get(0).title()).isEqualTo("Chapter One");
        assertThat(book.chapters().get(0).content()).contains("Hello EPUB chapter");
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
                        <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="chapter1"/>
                      </spine>
                    </package>
                    """);
            addZipEntry(zip, "OEBPS/chapter1.xhtml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>Chapter One</title></head>
                      <body><h1>Chapter One</h1><p>Hello EPUB chapter</p></body>
                    </html>
                    """);
            zip.finish();
            return output.toByteArray();
        }
    }

    private void addZipEntry(ZipOutputStream zip, String path, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
