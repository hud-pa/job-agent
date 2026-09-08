package job_agent.controller;

import job_agent.model.AppConfig;
import job_agent.service.AppConfigService;
import job_agent.service.GeminiService;
import job_agent.service.PdfExtractionService;
import job_agent.service.ProfileGenerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/config")
public class AppConfigController {

    private static final Logger log = LoggerFactory.getLogger(AppConfigController.class);

    private final AppConfigService appConfigService;
    private final GeminiService geminiService;
    private final PdfExtractionService pdfExtractionService;

    public AppConfigController(AppConfigService appConfigService,
                               GeminiService geminiService,
                               PdfExtractionService pdfExtractionService) {
        this.appConfigService = appConfigService;
        this.geminiService = geminiService;
        this.pdfExtractionService = pdfExtractionService;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> getAllConfig() {
        log.info("Fetching all application configurations.");
        List<AppConfig> allConfigs = appConfigService.getAllAppConfigs();
        Map<String, String> configMap = allConfigs.stream()
                .collect(Collectors.toMap(AppConfig::getConfigKey, AppConfig::getConfigValue));
        return ResponseEntity.ok(configMap);
    }

    @PutMapping("/{key}")
    public ResponseEntity<String> updateConfig(@PathVariable String key, @RequestBody String value) {
        log.info("Updating config key '{}' with value '{}'", key, value);
        appConfigService.setValue(key, value);
        return ResponseEntity.ok("Configuration updated successfully.");
    }

    @PostMapping(value = "/generate-profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> generateProfile(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "cv_text", required = false) String cvText,
            @RequestParam(value = "existing_json", required = false) String existingJson,
            @RequestParam(value = "update_existing", required = false, defaultValue = "true") boolean updateExisting) {
        log.info("Received request to generate/update candidate profile (updateExisting={}).", updateExisting);

        String rawManualText = (text != null && !text.isBlank()) ? text : (cvText != null ? cvText : "");
        StringBuilder combinedTextBuilder = new StringBuilder();

        if (file != null && !file.isEmpty()) {
            try {
                log.info("Extracting text from uploaded PDF file: {}", file.getOriginalFilename());
                byte[] bytes = file.getBytes();
                String pdfExtracted = pdfExtractionService.extractText(bytes);
                if (pdfExtracted != null && !pdfExtracted.isBlank()) {
                    combinedTextBuilder.append(pdfExtracted);
                }
            } catch (Exception e) {
                log.error("Failed to read uploaded PDF file bytes, falling back to manual text: {}", e.getMessage(), e);
            }
        }

        if (!rawManualText.isBlank()) {
            if (!combinedTextBuilder.isEmpty()) {
                combinedTextBuilder.append("\n\n");
            }
            combinedTextBuilder.append(rawManualText.trim());
            // Persist the user's latest text input in AppConfig
            appConfigService.setValue("cv_text", rawManualText.trim());
        }

        String combinedText = combinedTextBuilder.toString().trim();
        if (combinedText.isBlank()) {
            log.warn("Generation request failed: no PDF or text content provided.");
            return ResponseEntity.badRequest().body(Map.of("error", "No CV content provided. Please upload a PDF or enter your details and preferences."));
        }

        // Determine existing profile JSON to refine if updateExisting is enabled
        String profileToRefine = null;
        if (updateExisting) {
            if (existingJson != null && !existingJson.isBlank()) {
                profileToRefine = existingJson;
            } else {
                profileToRefine = appConfigService.getValue("cv_json");
            }
        }

        ProfileGenerationResult result = geminiService.generateCandidateProfile(combinedText, profileToRefine);
        if (result == null || result.cvJson() == null || result.cvJson().isBlank()) {
            log.error("Gemini failed to generate/update candidate profile.");
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to generate candidate profile using AI."));
        }

        // Save cv_json to AppConfig with the parsed JSON (without suggested_search_term)
        appConfigService.setValue("cv_json", result.cvJson());

        // Save search_term to AppConfig with the value of suggested_search_term
        if (result.suggestedSearchTerm() != null && !result.suggestedSearchTerm().isBlank()) {
            appConfigService.setValue("search_term", result.suggestedSearchTerm());
        }

        // Return both values in response
        Map<String, String> response = new HashMap<>();
        response.put("cv_json", result.cvJson());
        response.put("search_term", result.suggestedSearchTerm() != null ? result.suggestedSearchTerm() : "");

        log.info("Candidate profile generated/updated and configurations updated successfully.");
        return ResponseEntity.ok(response);
    }
}

