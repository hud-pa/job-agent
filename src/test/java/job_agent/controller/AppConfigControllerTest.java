package job_agent.controller;

import job_agent.service.AppConfigService;
import job_agent.service.GeminiService;
import job_agent.service.PdfExtractionService;
import job_agent.service.ProfileGenerationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppConfigControllerTest {

    @Mock
    private AppConfigService appConfigService;

    @Mock
    private GeminiService geminiService;

    @Mock
    private PdfExtractionService pdfExtractionService;

    @InjectMocks
    private AppConfigController controller;

    @Test
    void generateProfile_withTextOnly_success() {
        String inputCv = "Software Engineer with Java experience";
        String sampleJson = "{\"level\": \"Senior\", \"skills\": [\"Java\"]}";
        String sampleSearchTerm = "java developer zurich";

        when(geminiService.generateCandidateProfile(eq(inputCv), any()))
                .thenReturn(new ProfileGenerationResult(sampleJson, sampleSearchTerm));

        ResponseEntity<?> response = controller.generateProfile(null, inputCv, null, null, true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals(sampleJson, body.get("cv_json"));
        assertEquals(sampleSearchTerm, body.get("search_term"));

        verify(appConfigService).setValue("cv_json", sampleJson);
        verify(appConfigService).setValue("search_term", sampleSearchTerm);
        verify(appConfigService).setValue("cv_text", inputCv);
    }

    @Test
    void generateProfile_withPdfFile_extractsAndCallsGemini() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cv.pdf", "application/pdf", "dummy pdf content".getBytes());

        when(pdfExtractionService.extractText(any())).thenReturn("Extracted text from PDF");
        when(geminiService.generateCandidateProfile(eq("Extracted text from PDF"), any()))
                .thenReturn(new ProfileGenerationResult("{\"level\": \"Mid\"}", "software engineer"));

        ResponseEntity<?> response = controller.generateProfile(file, null, null, null, false);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(pdfExtractionService).extractText(any());
        verify(appConfigService).setValue("cv_json", "{\"level\": \"Mid\"}");
        verify(appConfigService).setValue("search_term", "software engineer");
    }

    @Test
    void generateProfile_withExistingJson_passesToGeminiToRefine() {
        String existingJson = "{\"level\": \"Mid\", \"skills\": [\"Java\"]}";
        String newNotes = "Prefer Bern and remote, fluent in German";
        String updatedJson = "{\"level\": \"Mid\", \"skills\": [\"Java\"], \"location\": {\"current\": \"Bern\"}}";

        when(geminiService.generateCandidateProfile(eq(newNotes), eq(existingJson)))
                .thenReturn(new ProfileGenerationResult(updatedJson, "java developer bern"));

        ResponseEntity<?> response = controller.generateProfile(null, newNotes, null, existingJson, true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(geminiService).generateCandidateProfile(eq(newNotes), eq(existingJson));
        verify(appConfigService).setValue("cv_json", updatedJson);
        verify(appConfigService).setValue("search_term", "java developer bern");
    }

    @Test
    void generateProfile_withEmptyInputs_returnsBadRequest() {
        ResponseEntity<?> response = controller.generateProfile(null, "", null, null, true);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(geminiService);
    }
}
