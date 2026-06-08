package job_agent.controller;

import job_agent.model.JobListing;
import job_agent.repository.JobListingRepository;
import job_agent.service.ScraperService;
import job_agent.service.JobEvaluationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public String triggerScrape(@RequestParam String term) {
        int found = scraperService.scrapeJobs(term);
        return "Scraping completed. Found " + found + " new jobs for term: " + term;
    }

    @PostMapping("/evaluate")
    public String triggerEvaluation() {
        int evaluated = evaluationService.evaluateNewJobs();
        return "Evaluation completed. Processed " + evaluated + " jobs.";
    }
}
