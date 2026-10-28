package com.aiworkflow.designer.document;

import com.aiworkflow.designer.document.service.DocumentParserService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentParserServiceTest {

    private final DocumentParserService parser = new DocumentParserService();

    @Test
    void extractsTextFromTxtFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "process.txt", "text/plain",
                "Step 1: receive order\nStep 2: approve".getBytes());

        String text = parser.extractText(file);

        assertThat(text).contains("receive order");
    }

    @Test
    void extractsTextFromPdf() throws IOException {
        byte[] pdf;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("Employee onboarding workflow");
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            pdf = out.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "workflow.pdf", "application/pdf", pdf);

        String text = parser.extractText(file);

        assertThat(text).contains("Employee onboarding workflow");
    }

    @Test
    void rejectsUnsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> parser.extractText(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported file type");
    }
}
