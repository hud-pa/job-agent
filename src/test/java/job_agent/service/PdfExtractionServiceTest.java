package job_agent.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PdfExtractionServiceTest {

    private PdfExtractionService pdfExtractionService;

    @BeforeEach
    void setUp() {
        pdfExtractionService = new PdfExtractionService();
    }

    @Test
    void extractText_fromValidPdf_returnsExtractedText() throws IOException {
        byte[] pdfBytes;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("Senior Java Developer with Spring Boot and AWS experience.");
                contentStream.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            pdfBytes = baos.toByteArray();
        }

        String extracted = pdfExtractionService.extractText(pdfBytes);
        assertNotNull(extracted);
        assertTrue(extracted.contains("Senior Java Developer"));
        assertTrue(extracted.contains("Spring Boot"));
    }

    @Test
    void extractText_withNullOrEmptyBytes_returnsEmptyString() {
        assertEquals("", pdfExtractionService.extractText(null));
        assertEquals("", pdfExtractionService.extractText(new byte[0]));
    }

    @Test
    void extractText_withCorruptPdfBytes_handlesGracefullyAndReturnsEmptyString() {
        byte[] corruptBytes = "Not a real PDF file".getBytes();
        String result = pdfExtractionService.extractText(corruptBytes);
        assertEquals("", result);
    }
}
