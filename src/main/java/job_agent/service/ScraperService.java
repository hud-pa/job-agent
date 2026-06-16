package job_agent.service;

import job_agent.model.JobListing;
import job_agent.repository.JobListingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Service
public class ScraperService {

    private static final Logger logger = LoggerFactory.getLogger(ScraperService.class);
    private final JobListingRepository repository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ScraperService(JobListingRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    public int scrapeJobs(String searchTerm) {
        String encodedTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
        // Using the internal API endpoint for more reliable data fetching
        String url = "https://job-search-api.jobs.ch/search/semantic?query=" + encodedTerm + "&rows=20";

        int newJobsCount = 0;
        try {
            logger.info("Fetching jobs from API for term: {}", searchTerm);

            // Set headers to mimic a browser request
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
            JsonNode root = response.getBody();

            if (root != null && root.has("documents")) {
                JsonNode results = root.get("documents");
                logger.info("API returned {} job listings", results.size());

                for (JsonNode node : results) {
                    String title = node.path("title").asText();
                    String company = node.path("company").path("name").asText("Unknown Company");
                    String place = node.path("place").asText("Switzerland");
                    
                    // Construct the detail URL using the job ID
                    String jobId = node.path("id").asText();
                    String jobUrl = "https://www.jobs.ch/en/vacancies/detail/" + jobId + "/";

                    if (jobUrl.isEmpty()) continue;

                    if (repository.findByUrl(jobUrl).isEmpty()) {
                        JobListing job = new JobListing();
                        job.setTitle(title);
                        job.setCompany(company);
                        job.setLocation(place);
                        job.setUrl(jobUrl);
                        
                        // Enrich with full description from JSON-LD
                        String fullJsonLd = fetchJobDescription(jobUrl);
                        job.setDescription(fullJsonLd);

                        job.setSource("jobs.ch API");
                        job.setSeen(false);
                        job.setStatus("new");
                        job.setDateFound(LocalDate.now());
                        
                        repository.save(job);
                        newJobsCount++;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error during scraping jobs for term: {}", searchTerm, e);
        }
        return newJobsCount;
    }

    private String fetchJobDescription(String url) {
        try {
            // Politeness delay
            Thread.sleep(800);

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .timeout(15000)
                    .get();

            // Corrected selector with quotes
            Elements scripts = doc.select("script[type='application/ld+json']");
            logger.debug("Found {} JSON-LD scripts at URL: {}", scripts.size(), url);

            for (var script : scripts) {
                String content = script.data().trim();
                if (content.isEmpty()) continue;

                JsonNode node = objectMapper.readTree(content);

                // Handle both single object and array of objects
                if (node.isArray()) {
                    for (JsonNode element : node) {
                        if (isJobPosting(element)) {
                            logger.info("Successfully extracted JSON-LD (Array) for URL: {}", url);
                            return content;
                        }
                    }
                } else if (isJobPosting(node)) {
                    logger.info("Successfully extracted JSON-LD (Object) for URL: {}", url);
                    return content;
                }
            }
            logger.warn("No JobPosting JSON-LD found at URL: {}", url);
        } catch (Exception e) {
            logger.error("Failed to fetch job description from: {}", url, e);
        }
        return null;
    }

    private boolean isJobPosting(JsonNode node) {
        String type = node.path("@type").asText();
        return "JobPosting".equals(type);
    }
}
