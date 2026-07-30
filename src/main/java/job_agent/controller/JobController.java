package job_agent.controller;

import job_agent.model.JobListing;
import job_agent.repository.JobListingRepository;
import job_agent.service.ScraperService;
import job_agent.service.JobEvaluationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final ScraperService scraperService;
    private final JobListingRepository repository;
    private final JobEvaluationService evaluationService;

    public JobController(ScraperService scraperService, JobListingRepository repository, JobEvaluationService evaluationService) {
        this.scraperService = scraperService;
        this.repository = repository;
        this.evaluationService = evaluationService;
    }

    @GetMapping
    public List<JobListing> getAllJobs() {
        return repository.findAll();
    }

    @PostMapping("/scrape")
    public String triggerScrape() {
        int found = scraperService.scrapeJobs();
        return "Scraping completed. Found " + found + " new jobs.";
    }

    @PostMapping("/evaluate")
    public String triggerEvaluation() {
        int evaluated = evaluationService.evaluateNewJobListings();
        return "Evaluation completed. Processed " + evaluated + " jobs.";
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable UUID id) {
        repository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/seen")
    public ResponseEntity<Void> markAsSeen(@PathVariable UUID id) {
        repository.findById(id).ifPresent(job -> {
            job.setSeen(true);
            repository.save(job);
        });
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        repository.findById(id).ifPresent(job -> {
            job.setStatus(body.get("status"));
            repository.save(job);
        });
        return ResponseEntity.ok().build();
    }
}
