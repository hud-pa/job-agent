package job_agent.service;

import job_agent.model.JobListing;
import job_agent.repository.JobListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JobEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(JobEvaluationService.class);
    private final JobListingRepository jobListingRepository;
    private final GeminiService geminiService;
    private final AppConfigService appConfigService;

    public JobEvaluationService(JobListingRepository jobListingRepository, 
                                GeminiService geminiService,
                                AppConfigService appConfigService) {
        this.jobListingRepository = jobListingRepository;
        this.geminiService = geminiService;
        this.appConfigService = appConfigService;
    }

    @Transactional
    public int evaluateNewJobListings() {
        log.info("Starting evaluation of new job listings...");
        List<JobListing> newJobListings = jobListingRepository.findByAiEvaluatedFalse();

        if (newJobListings.isEmpty()) {
            log.info("No new job listings to evaluate.");
            return 0;
        }

        // Get min_ai_score from AppConfigService
        String minAiScoreStr = appConfigService.getValue("min_ai_score");
        int minAiScore = 40; // Default fallback
        if (minAiScoreStr != null) {
            try {
                minAiScore = Integer.parseInt(minAiScoreStr);
            } catch (NumberFormatException e) {
                log.error("Invalid min_ai_score in AppConfig: {}. Using default 40.", minAiScoreStr);
            }
        }
        log.info("Minimum AI score for evaluation: {}", minAiScore);

        int processedCount = 0;

        for (JobListing listing : newJobListings) {
            try {
                log.info("Evaluating job listing: {}", listing.getUrl());
                // Assuming JobListing has a getDescription() method or similar for the job content
                // If not, ScraperService needs to be updated to fetch and store the full description.
                // For now, I'll use listing.getUrl() as a placeholder, but this needs to be the actual job content.
                // TODO: Update ScraperService to fetch and store job description in JobListing.
                GeminiService.GeminiEvaluationResult result = geminiService.evaluateJobListing(listing.getUrl()); // This should be listing.getDescription()

                if (result.getScore() == -1) {
                    log.warn("Technical error or parsing failure for job {}. Skipping for now.", listing.getUrl());
                    continue;
                }

                if (result.getScore() >= minAiScore) { // Use dynamic minAiScore
                    listing.setAiScore(result.getScore());
                    listing.setAiReasoning(result.getReasoning());
                    listing.setAiEvaluated(true);
                    listing.setStatus("evaluated");
                    jobListingRepository.save(listing);
                    log.info("Job listing {} evaluated and saved with score {}.", listing.getId(), result.getScore());
                } else { // score >= 0 AND score < minAiScore
                    jobListingRepository.delete(listing);
                    log.info("Job listing {} deleted due to low AI score {}.", listing.getId(), result.getScore());
                }

                // Add 5 second delay for rate limiting
                Thread.sleep(5000);
                processedCount++;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Job evaluation interrupted.", e);
                return processedCount;
            } catch (Exception e) {
                log.error("Error evaluating job listing {}: {}", listing.getUrl(), e.getMessage());
                // Continue with next listing even if one fails, but don't mark this one as evaluated
            }
        }
        log.info("Finished evaluation of new job listings.");
        return processedCount;
    }
}
