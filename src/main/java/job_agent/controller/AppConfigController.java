package job_agent.controller;

import job_agent.model.AppConfig;
import job_agent.service.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/config")
public class AppConfigController {

    private static final Logger log = LoggerFactory.getLogger(AppConfigController.class);
    private final AppConfigService appConfigService;

    public AppConfigController(AppConfigService appConfigService) {
        this.appConfigService = appConfigService;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> getAllConfig() {
        log.info("Fetching all application configurations.");
        // Assuming AppConfigService can provide all configs, or we need to add a method for it.
        // For now, let's assume we can get all and convert to a map.
        // This will require a new method in AppConfigService or AppConfigRepository.
        // Let's add a temporary placeholder and refine AppConfigService later if needed.
        // For now, AppConfigService only has getValue, so we'll return a dummy or empty map.
        // A better approach would be to fetch all from repository.
        List<AppConfig> allConfigs = appConfigService.getAllAppConfigs(); // This method needs to be added
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
}
