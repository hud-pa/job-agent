package job_agent.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class PdfExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractionService.class);

    /**
     * Extracts plain text from the given PDF byte array in-memory without saving it to disk.
     * Gracefully handles errors and returns an empty string if extraction fails.
     *
     * @param pdfBytes the PDF file content as a byte array
     * @return extracted text, or empty string if input is empty or parsing fails
     */
    public String extractText(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return "";
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("Successfully extracted {} characters from uploaded PDF.", text != null ? text.length() : 0);
            return text != null ? text.trim() : "";
        } catch (IOException | RuntimeException e) {
            log.error("Failed to parse PDF document, falling back gracefully: {}", e.getMessage(), e);
            return "";
        }
    }
}
