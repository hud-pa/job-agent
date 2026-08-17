package job_agent.controller;

import job_agent.model.AppConfig;
import job_agent.repository.JobListingRepository;
import job_agent.service.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import job_agent.model.JobListing;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class JobViewController {

    private static final Logger log = LoggerFactory.getLogger(JobViewController.class);
    private final JobListingRepository jobListingRepository;
    private final AppConfigService appConfigService;

    public JobViewController(JobListingRepository jobListingRepository, AppConfigService appConfigService) {
        this.jobListingRepository = jobListingRepository;
        this.appConfigService = appConfigService;
    }

    @GetMapping("/")
    public String redirectToJobs() {
        return "redirect:/jobs";
    }

    @GetMapping("/jobs")
    public String showJobs(Model model) {
        log.info("Serving jobs page.");
        // Initially load all jobs, filtering will be done via JS on the client side
        model.addAttribute("jobs", jobListingRepository.findAll()); 
        return "jobs";
    }

    @GetMapping("/jobs/{id}")
    public String showJobDetail(@PathVariable UUID id, Model model) {
        log.info("Serving job detail page for job id: {}", id);
        Optional<JobListing> jobOptional = jobListingRepository.findById(id);
        if (jobOptional.isEmpty()) {
            log.warn("Job with id {} not found", id);
            return "redirect:/jobs";
        }
        model.addAttribute("job", jobOptional.get());
        return "job-detail";
    }

    @GetMapping("/settings")
    public String showSettings(Model model) {
        log.info("Serving settings page.");
        Map<String, String> settings = appConfigService.getAllAppConfigs().stream()
                .collect(Collectors.toMap(AppConfig::getConfigKey, AppConfig::getConfigValue));
        model.addAttribute("settings", settings);
        return "settings";
    }
}

