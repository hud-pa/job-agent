package job_agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AppConfigService appConfigService;
    private final String geminiApiKey;

    public GeminiService(RestTemplateBuilder restTemplateBuilder,
                         ObjectMapper objectMapper,
                         AppConfigService appConfigService,
                         @Value("${gemini.api.key}") String geminiApiKey) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = objectMapper;
        this.appConfigService = appConfigService;
        this.geminiApiKey = geminiApiKey;
    }

    /**
     * Evaluates a job listing against the candidate profile stored in AppConfig.
     *
     * @param jobDescription the raw job description text or JSON-LD content
     * @return a fully populated {@link EvaluationResult}, or {@code null} if the API
     *         call fails or the response cannot be parsed
     */
    public EvaluationResult evaluateJobListing(String jobDescription) {
        String prompt = buildPrompt(jobDescription);
        String requestBody;
        try {
            requestBody = buildRequestBody(prompt);
        } catch (JsonProcessingException e) {
            log.error("Failed to build Gemini request body", e);
            return null;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        String rawResponse;
        try {
            rawResponse = restTemplate.postForObject(GEMINI_API_URL + geminiApiKey, entity, String.class);
        } catch (RestClientException e) {
            log.error("Error calling Gemini API", e);
            return null;
        }

        return parseGeminiResponse(rawResponse);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String buildPrompt(String jobDescription) {
        String cvJson = appConfigService.getValue("cv_json");
        if (cvJson == null || cvJson.isBlank()) {
            cvJson = """
                    {
                      "level": "Junior",
                      "skills": ["Java", "Spring Boot", "PostgreSQL"],
                      "languages": {"english": "B2"},
                      "location": "Bern, Switzerland"
                    }""";
            log.warn("cv_json not found in AppConfig – using built-in fallback profile for Gemini prompt.");
        }

        return """
                You are a job matching assistant. Evaluate if this job matches the candidate profile.

                CANDIDATE PROFILE:
                %s

                JOB DESCRIPTION:
                %s

                Respond ONLY with this exact JSON, no markdown, no extra text, no code blocks:
                {
                  "overall_score": <0-100>,
                  "technical_match": <0-100>,
                  "language_match": <0-100>,
                  "level_match": <0-100>,
                  "location_match": <0-100>,
                  "strengths": ["skill1", "skill2"],
                  "missing_skills": ["skill1", "skill2"],
                  "summary": "2-3 sentence explanation why this job matches or not",
                  "recommendation": "APPLY" or "CONSIDER" or "SKIP"
                }""".formatted(cvJson, jobDescription);
    }

    private String buildRequestBody(String prompt) throws JsonProcessingException {
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", Collections.singletonList(part));

        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("contents", Collections.singletonList(content));

        return objectMapper.writeValueAsString(requestBodyMap);
    }

    /**
     * Extracts the text payload from the Gemini response envelope, strips any
     * accidental markdown fences the model may have added, and maps it to
     * {@link EvaluationResult}.
     *
     * @return the parsed result, or {@code null} if the response cannot be parsed
     */
    private EvaluationResult parseGeminiResponse(String rawResponse) {
        if (rawResponse == null) {
            log.error("Gemini returned a null response body");
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                log.error("Gemini response contains no candidates: {}", rawResponse);
                return null;
            }

            String rawText = candidates.get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText()
                    .trim();

            // Strip optional markdown code fences the model sometimes adds
            String jsonText = rawText;
            if (rawText.contains("```json")) {
                jsonText = rawText.substring(rawText.indexOf("```json") + 7,
                                             rawText.lastIndexOf("```")).trim();
            } else if (rawText.contains("```")) {
                jsonText = rawText.substring(rawText.indexOf("```") + 3,
                                             rawText.lastIndexOf("```")).trim();
            }

            JsonNode result = objectMapper.readTree(jsonText);

            int overallScore   = result.path("overall_score").asInt(0);
            int technicalMatch = result.path("technical_match").asInt(0);
            int languageMatch  = result.path("language_match").asInt(0);
            int levelMatch     = result.path("level_match").asInt(0);
            int locationMatch  = result.path("location_match").asInt(0);
            String summary        = result.path("summary").asText("N/A");
            String recommendation = result.path("recommendation").asText("SKIP");

            List<String> strengths = objectMapper.convertValue(
                    result.path("strengths"), new TypeReference<List<String>>() {});
            List<String> missingSkills = objectMapper.convertValue(
                    result.path("missing_skills"), new TypeReference<List<String>>() {});

            return new EvaluationResult(
                    overallScore, technicalMatch, languageMatch,
                    levelMatch, locationMatch,
                    strengths, missingSkills,
                    summary, recommendation);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse Gemini response JSON. Raw response: {}", rawResponse, e);
            return null;
        }
    }
}
