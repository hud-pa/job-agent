package job_agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=";

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
        String jsonText = executePrompt(prompt);
        if (jsonText == null) {
            return null;
        }

        return parseGeminiResponse(jsonText);
    }

    /**
     * Generates a structured candidate profile JSON and a suggested job search term
     * from raw CV text using the Gemini API.
     *
     * @param cvText combined CV text (from uploaded PDF and/or manual text)
     * @return a {@link ProfileGenerationResult} containing the clean candidate JSON and suggested search term,
     *         or {@code null} if generation fails
     */
    public ProfileGenerationResult generateCandidateProfile(String cvText) {
        return generateCandidateProfile(cvText, null);
    }

    /**
     * Generates or updates a structured candidate profile JSON and suggested job search term.
     * If an existing candidate JSON profile is provided, the AI updates and enriches it rather
     * than wiping existing details.
     *
     * @param cvText combined CV or preferences text
     * @param existingCvJson current candidate JSON profile to refine, or null/empty for fresh generation
     * @return a {@link ProfileGenerationResult}, or {@code null} on failure
     */
    public ProfileGenerationResult generateCandidateProfile(String cvText, String existingCvJson) {
        if (cvText == null || cvText.isBlank()) {
            log.warn("Cannot generate candidate profile from empty CV text.");
            return null;
        }

        String prompt = (existingCvJson != null && !existingCvJson.isBlank())
                ? buildProfileRefinementPrompt(cvText, existingCvJson)
                : buildProfileGenerationPrompt(cvText);

        String jsonText = executePrompt(prompt);
        if (jsonText == null || jsonText.isBlank()) {
            log.error("Empty or null response received from Gemini for profile generation.");
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(jsonText);
            if (!root.isObject()) {
                log.error("Gemini profile generation response is not a JSON object: {}", jsonText);
                return null;
            }

            ObjectNode objectNode = (ObjectNode) root;
            String suggestedSearchTerm = "";
            if (objectNode.has("suggested_search_term")) {
                suggestedSearchTerm = objectNode.get("suggested_search_term").asText("").trim();
                objectNode.remove("suggested_search_term");
            }

            String cleanedCvJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectNode);
            return new ProfileGenerationResult(cleanedCvJson, suggestedSearchTerm);
        } catch (Exception e) {
            log.error("Failed to parse candidate profile JSON from Gemini response: {}", jsonText, e);
            return null;
        }
    }

    /**
     * Calls the Gemini API with the given prompt and returns the extracted, code-fence stripped text.
     */
    public String executePrompt(String prompt) {
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

        if (rawResponse == null) {
            log.error("Gemini returned a null response body");
            return null;
        }

        return extractTextFromResponse(rawResponse);
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

        String cvText = appConfigService.getValue("cv_text");
        if (cvText == null || cvText.isBlank()) {
            cvText = "Candidate is a Java Developer looking for opportunities in Switzerland.";
        }

        return """
                You are a job matching assistant. Evaluate if this job matches the candidate.

                CANDIDATE STRUCTURED CONSTRAINTS (JSON):
                %s

                CANDIDATE FULL CV & PREFERENCES (TEXT):
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
                  "summary": "2-4 sentence explanation why this job matches or not",
                  "recommendation": "APPLY" or "CONSIDER" or "SKIP"
                }""".formatted(cvJson, cvText, jobDescription);
    }

    private String buildProfileGenerationPrompt(String cvText) {
        return """
                You are a CV parsing assistant. Extract structured candidate information from the following CV text and return ONLY a valid JSON object with no markdown, no code blocks, no extra text.

                CV TEXT:
                %s

                Return this exact JSON structure:
                {
                  "level": "Junior" or "Mid" or "Senior",
                  "skills": ["skill1", "skill2", ...],
                  "languages": {"language": "level", ...},
                  "location": {
                    "current": "city, country",
                    "permit": "permit type if mentioned",
                    "preferred_cantons": ["canton1", ...]
                  },
                  "experience_years": <number>,
                  "preferred_roles": ["role1", "role2", ...],
                  "projects": ["project description", ...]
                }

                Also generate a short job search term (2-4 words, in English or German) best matching this candidate for Swiss job market. Return it as an additional field:
                "suggested_search_term": "java developer bern"
                """.formatted(cvText);
    }

    private String buildProfileRefinementPrompt(String cvText, String existingCvJson) {
        return """
                You are a CV parsing and candidate profile refinement assistant.
                You are provided with an EXISTING CANDIDATE PROFILE (JSON) and NEW / ADDITIONAL CV AND PREFERENCES TEXT.

                Your objective is to UPDATE, ENRICH, and REFINE the existing profile:
                1. Incorporate any newly mentioned skills, languages, location preferences, work permits, or preferred roles.
                2. If the user clarifies or specifies adjustments (e.g. higher seniority, specific cantons, new experience years, updated preferences), apply those updates.
                3. Retain and preserve existing valid skills, projects, and details unless explicitly contradicted, refined, or replaced by the new text.
                4. Do NOT wipe out or drop existing valid information unless instructed.
                5. Return ONLY a valid JSON object matching the schema below, with no markdown, no code blocks, no extra text.

                EXISTING CANDIDATE PROFILE (JSON):
                %s

                NEW / ADDITIONAL CV & PREFERENCES TEXT:
                %s

                Return this exact JSON structure:
                {
                  "level": "Junior" or "Mid" or "Senior",
                  "skills": ["skill1", "skill2", ...],
                  "languages": {"language": "level", ...},
                  "location": {
                    "current": "city, country",
                    "permit": "permit type if mentioned",
                    "preferred_cantons": ["canton1", ...]
                  },
                  "experience_years": <number>,
                  "preferred_roles": ["role1", "role2", ...],
                  "projects": ["project description", ...]
                }

                Also generate a short job search term (2-4 words, in English or German) best matching this updated candidate for the Swiss job market. Return it as an additional field:
                "suggested_search_term": "java developer bern"
                """.formatted(existingCvJson, cvText);
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

    private String extractTextFromResponse(String rawResponse) {
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

            return jsonText;
        } catch (Exception e) {
            log.error("Failed to extract text from Gemini response: {}", rawResponse, e);
            return null;
        }
    }

    /**
     * Maps cleaned JSON string to {@link EvaluationResult}.
     *
     * @return the parsed result, or {@code null} if the response cannot be parsed
     */
    private EvaluationResult parseGeminiResponse(String jsonText) {
        try {
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
            log.error("Failed to parse Gemini response JSON: {}", jsonText, e);
            return null;
        }
    }
}

