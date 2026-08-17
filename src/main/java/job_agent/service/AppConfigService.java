package job_agent.service;

import jakarta.annotation.PostConstruct;
import job_agent.model.AppConfig;
import job_agent.repository.AppConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AppConfigService {

    private static final Logger log = LoggerFactory.getLogger(AppConfigService.class);
    private final AppConfigRepository appConfigRepository;

    public AppConfigService(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    public String getValue(String key) {
        return appConfigRepository.findByConfigKey(key)
                .map(AppConfig::getConfigValue)
                .orElse(null);
    }

    public List<AppConfig> getAllAppConfigs() {
        return appConfigRepository.findAll();
    }

    @Transactional
    public void setValue(String key, String value) {
        Optional<AppConfig> existingConfig = appConfigRepository.findByConfigKey(key);
        if (existingConfig.isPresent()) {
            AppConfig config = existingConfig.get();
            config.setConfigValue(value);
            appConfigRepository.save(config);
            log.info("Updated config key '{}' with value '{}'", key, value);
        } else {
            AppConfig newConfig = new AppConfig(key, value, null); // Description can be added later if needed
            appConfigRepository.save(newConfig);
            log.info("Created new config key '{}' with value '{}'", key, value);
        }
    }

    @PostConstruct
    public void initializeDefaults() {
        log.info("Initializing default application configurations...");

        // Define default configurations
        addDefaultConfig("search_term", "java bern", "Default search term for job scraping");
        addDefaultConfig("min_ai_score", "40", "Minimum AI score for a job listing to be considered relevant");
        addDefaultConfig("cv_text", "Java Developer with MSc in Informatics. Skills: Java (Advanced), Spring Boot, Hibernate, PostgreSQL, REST API, AWS (Basic), Git, Agile/Scrum. Junior/Mid level. Location preference: Bern, Switzerland.", "Candidate's CV text used for AI evaluation (legacy – prefer cv_json)");
        addDefaultConfig("cv_json",
                """
                {
                  "level": "Junior",
                  "skills": ["Java", "Spring Boot", "PostgreSQL", "Hibernate", "AWS", "REST API", "Git", "Agile"],
                  "languages": {"slovak": "native", "english": "B2", "german": "A2"},
                  "location": "Bern, Switzerland",
                  "experience_years": 1,
                  "preferred_roles": ["Java Developer", "Backend Developer", "IT Specialist"]
                }""",
                "Structured candidate profile JSON used by GeminiService for job matching");

        log.info("Default application configurations initialized.");
    }

    @Transactional
    private void addDefaultConfig(String key, String defaultValue, String description) {
        if (appConfigRepository.findByConfigKey(key).isEmpty()) {
            appConfigRepository.save(new AppConfig(key, defaultValue, description));
            log.debug("Added default config: {} = {}", key, defaultValue);
        }
    }
}
