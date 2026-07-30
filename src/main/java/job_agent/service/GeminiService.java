package job_agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AppConfigService appConfigService; // Inject AppConfigService

    private final String geminiApiKey;

    private final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=";


    public GeminiService(RestTemplateBuilder restTemplateBuilder, 
                         ObjectMapper objectMapper,
                         AppConfigService appConfigService, // Add AppConfigService to constructor
                         @Value("${gemini.api.key}") String geminiApiKey) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
        this.appConfigService = appConfigService; // Assign AppConfigService
        this.geminiApiKey = geminiApiKey;
    }

    public GeminiEvaluationResult evaluateJobListing(String description) {
        String prompt = buildPrompt(description);
        String requestBody = buildRequestBody(prompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            String response = restTemplate.postForObject(GEMINI_API_URL + geminiApiKey, entity, String.class);
            return parseGeminiResponse(response);
        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            return new GeminiEvaluationResult(-1, "Error during AI evaluation: " + e.getMessage());
        }
    }

    private String buildPrompt(String description) {
        String cvText = appConfigService.getValue("cv_text"); // Get cv_text from AppConfigService
        if (cvText == null || cvText.isEmpty()) {
            cvText = "Junior/Mid level Java Developer with Spring Boot experience."; // Fallback if config not found
            log.warn("cv_text not found in AppConfig, using default fallback text for Gemini prompt.");
        }

        return "Analyze the following job description (provided in JSON-LD or text format) " +
               "and evaluate whether it is suitable for a candidate with the following experience:\n" +
               cvText + "\n" + // Use dynamic cv_text
               "Respond ONLY in this JSON format:\n" +
               "{\"score\": 75, \"reasoning\": \"reason\"}\n\n" +
               "Job Content:\n" + description;
    }

    private String buildRequestBody(String prompt) {
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", Collections.singletonList(part));

        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("contents", Collections.singletonList(content));

        try {
            return objectMapper.writeValueAsString(requestBodyMap);
        } catch (Exception e) {
            log.error("Error building Gemini request body", e);
            return "{}"; // Should not happen
        }
    }

    private GeminiEvaluationResult parseGeminiResponse(String response) {
        try {
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode candidates = rootNode.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    String rawText = parts.get(0).path("text").asText().trim();
                    
                    // Clean up potential markdown formatting from the AI response
                    String jsonContent = rawText;
                    if (rawText.contains("```json")) {
                        jsonContent = rawText.substring(rawText.indexOf("```json") + 7, rawText.lastIndexOf("```"));
                    } else if (rawText.contains("```")) {
                        jsonContent = rawText.substring(rawText.indexOf("```") + 3, rawText.lastIndexOf("```"));
                    }

                    JsonNode jsonResponse = objectMapper.readTree(jsonContent);
                    int score = jsonResponse.path("score").asInt(0);
                    String reasoning = jsonResponse.path("reasoning").asText("N/A");
                    
                    return new GeminiEvaluationResult(score, reasoning);
                }
            }
        } catch (Exception e) {
            log.error("Error parsing Gemini API response: {}", response, e);
        }
        return new GeminiEvaluationResult(-1, "Failed to parse Gemini response.");
    }

    public static class GeminiEvaluationResult {
        private final int score;
        private final String reasoning;

        public GeminiEvaluationResult(int score, String reasoning) {
            this.score = score;
            this.reasoning = reasoning;
        }

        public int getScore() {
            return score;
        }

        public String getReasoning() {
            return reasoning;
        }
    }
}
