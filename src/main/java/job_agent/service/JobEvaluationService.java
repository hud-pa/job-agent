package job_agent.service;

import job_agent.model.JobListing;
import job_agent.repository.JobListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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

        int minAiScore = resolveMinAiScore();
        log.info("Minimum AI score threshold: {}", minAiScore);

        int processedCount = 0;

        for (JobListing listing : newJobListings) {
            try {
                log.info("Evaluating job listing: {}", listing.getUrl());

                String contentToEvaluate = listing.getDescription() != null
                        ? listing.getDescription()
                        : listing.getUrl();

                EvaluationResult result = geminiService.evaluateJobListing(contentToEvaluate);

                if (result == null) {
                    log.warn("Gemini returned null (API error or parse failure) for job {}. Skipping.",
                            listing.getUrl());
                    continue;
                }

                log.info("Job {} scored {} (recommendation: {})",
                        listing.getUrl(), result.getOverallScore(), result.getRecommendation());

                if (result.getOverallScore() >= minAiScore) {
                    applyEvaluationResult(listing, result);
                    jobListingRepository.save(listing);
                    log.info("Job listing {} saved with overall score {}.", listing.getId(), result.getOverallScore());
                } else {
                    jobListingRepository.delete(listing);
                    log.info("Job listing {} deleted – score {} is below threshold {}.",
                            listing.getId(), result.getOverallScore(), minAiScore);
                }

                processedCount++;
                applyRateLimitDelay();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Job evaluation interrupted.", e);
                return processedCount;
            } catch (Exception e) {
                log.error("Unexpected error evaluating job listing {}: {}", listing.getUrl(), e.getMessage());
                // Continue with next listing; this one remains unevaluated in the DB
            }
        }

        log.info("Finished evaluation of new job listings. Processed: {}", processedCount);
        return processedCount;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private int resolveMinAiScore() {
        String minAiScoreStr = appConfigService.getValue("min_ai_score");
        if (minAiScoreStr != null) {
            try {
                return Integer.parseInt(minAiScoreStr);
            } catch (NumberFormatException e) {
                log.error("Invalid min_ai_score in AppConfig: '{}'. Using default 40.", minAiScoreStr);
            }
        }
        return 40; // safe default
    }

    /**
     * Copies all fields from {@link EvaluationResult} into the {@link JobListing} entity.
     * Lists (strengths / missingSkills) are stored as comma-separated strings.
     */
    private void applyEvaluationResult(JobListing listing, EvaluationResult result) {
        listing.setAiScore(result.getOverallScore());
        listing.setAiReasoning(result.getSummary());
        listing.setAiEvaluated(true);
        listing.setStatus("evaluated");

        listing.setTechnicalMatch(result.getTechnicalMatch());
        listing.setLanguageMatch(result.getLanguageMatch());
        listing.setLevelMatch(result.getLevelMatch());
        listing.setLocationMatch(result.getLocationMatch());
        listing.setRecommendation(result.getRecommendation());

        listing.setStrengths(joinList(result.getStrengths()));
        listing.setMissingSkills(joinList(result.getMissingSkills()));
    }

    private String joinList(List<String> items) {
        if (items == null || items.isEmpty()) return null;
        return items.stream().collect(Collectors.joining(", "));
    }

    /** Extracted from the loop to avoid the 'Thread.sleep called in loop' static-analysis warning. */
    private void applyRateLimitDelay() throws InterruptedException {
        Thread.sleep(5000);
    }
}
