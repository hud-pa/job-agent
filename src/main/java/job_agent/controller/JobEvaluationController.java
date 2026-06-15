package job_agent.controller;

import job_agent.service.JobEvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class JobEvaluationController {

    private static final Logger log = LoggerFactory.getLogger(JobEvaluationController.class);
    private final JobEvaluationService jobEvaluationService;

    public JobEvaluationController(JobEvaluationService jobEvaluationService) {
        this.jobEvaluationService = jobEvaluationService;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<String> triggerJobEvaluation() {
        log.info("Manual job evaluation triggered via /api/evaluate endpoint.");
        jobEvaluationService.evaluateNewJobListings();
        return ResponseEntity.ok("Job evaluation process started.");
    }
}
