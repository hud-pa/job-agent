package job_agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeminiServiceTest {

    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private AppConfigService appConfigService;

    private ObjectMapper objectMapper;
    private GeminiService geminiService;

    @BeforeEach
    void setUp() {
        when(restTemplateBuilder.connectTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.readTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);

        objectMapper = new ObjectMapper();
        geminiService = new GeminiService(restTemplateBuilder, objectMapper, appConfigService, "dummy-key");
    }

    @Test
    void generateCandidateProfile_success() {
        String geminiApiResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "```json\\n{\\n  \\"level\\": \\"Senior\\",\\n  \\"skills\\": [\\"Java\\", \\"Spring Boot\\"],\\n  \\"languages\\": {\\"english\\": \\"C1\\"},\\n  \\"location\\": {\\"current\\": \\"Bern\\"},\\n  \\"experience_years\\": 5,\\n  \\"preferred_roles\\": [\\"Backend Engineer\\"],\\n  \\"projects\\": [\\"Cloud migration\\"],\\n  \\"suggested_search_term\\": \\"senior java bern\\"\\n}\\n```"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(geminiApiResponse);

        ProfileGenerationResult result = geminiService.generateCandidateProfile("5 years Java experience");

        assertNotNull(result);
        assertEquals("senior java bern", result.suggestedSearchTerm());
        assertNotNull(result.cvJson());
        // Verify suggested_search_term was removed from cvJson
        assertFalse(result.cvJson().contains("suggested_search_term"));
        assertTrue(result.cvJson().contains("Senior"));
        assertTrue(result.cvJson().contains("Java"));
    }

    @Test
    void generateCandidateProfile_withExistingCvJson_refinesProfile() {
        String existingCvJson = "{\"level\": \"Mid\", \"skills\": [\"Java\"]}";
        String geminiApiResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"level\\": \\"Senior\\", \\"skills\\": [\\"Java\\", \\"AWS\\"], \\"suggested_search_term\\": \\"senior cloud java\\"}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(geminiApiResponse);

        ProfileGenerationResult result = geminiService.generateCandidateProfile("Added AWS certification", existingCvJson);

        assertNotNull(result);
        assertEquals("senior cloud java", result.suggestedSearchTerm());
        assertTrue(result.cvJson().contains("AWS"));
        assertFalse(result.cvJson().contains("suggested_search_term"));
    }

    @Test
    void generateCandidateProfile_withEmptyInput_returnsNull() {
        ProfileGenerationResult result = geminiService.generateCandidateProfile("   ");
        assertNull(result);
        verifyNoInteractions(restTemplate);
    }
}
