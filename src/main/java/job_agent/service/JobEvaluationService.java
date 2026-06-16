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

    public JobEvaluationService(JobListingRepository jobListingRepository, GeminiService geminiService) {
        this.jobListingRepository = jobListingRepository;
        this.geminiService = geminiService;
    }

    @Transactional
    public int evaluateNewJobListings() {
        log.info("Starting evaluation of new job listings...");
        List<JobListing> newJobListings = jobListingRepository.findByAiEvaluatedFalse();

        if (newJobListings.isEmpty()) {
            log.info("No new job listings to evaluate.");
            return 0;
        }

        int processedCount = 0;

        for (JobListing listing : newJobListings) {
            try {
                log.info("Evaluating job listing: {}", listing.getUrl());
                GeminiService.GeminiEvaluationResult result = geminiService.evaluateJobListing(listing.getDescription());

                if (result.getScore() == -1) {
                    log.warn("Technical error or parsing failure for job {}. Stopping testing loop.", listing.getUrl());
                    // Changed continue to break to stop after the first attempt even on error
                    break;
                }

                if (result.getScore() >= 40) {
                    listing.setAiScore(result.getScore());
                    listing.setAiReasoning(result.getReasoning());
                    listing.setAiEvaluated(true);
                    listing.setStatus("evaluated");
                    jobListingRepository.save(listing);
                    log.info("Job listing {} evaluated and saved with score {}.", listing.getId(), result.getScore());
                } else { // score >= 0 AND score < 40
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
                // Stop after first attempt even if an exception occurs
                break;
            }
            
            // Raw limit: exit the loop after the first iteration to save Gemini tokens during testing
            break;
        }
        log.info("Finished evaluation of new job listings.");
        return processedCount;
    }
}
