package job_agent.service;

import job_agent.model.JobListing;
import job_agent.repository.JobListingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;

@Service
public class JobEvaluationService {

    private final JobListingRepository repository;
    private final Random random = new Random();

    public JobEvaluationService(JobListingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int evaluateNewJobs() {
        List<JobListing> newJobs = repository.findByStatus("new");
        for (JobListing job : newJobs) {
            // Placeholder for AI logic (Gemini API)
            int mockScore = random.nextInt(100) + 1;
            job.setAiScore(mockScore);
            job.setAiReasoning("Mock evaluation: Title contains relevant keywords.");
            job.setStatus("evaluated");
            repository.save(job);
        }
        return newJobs.size();
    }
}