package job_agent.service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import job_agent.model.JobListing;
import job_agent.repository.JobListingRepository;

@Service
public class ScraperService {

    private static final Logger logger = LoggerFactory.getLogger(ScraperService.class);
    private static final int PAGE_SIZE = 50;

    private final JobListingRepository repository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AppConfigService appConfigService;

    public ScraperService(JobListingRepository repository,
                          ObjectMapper objectMapper,
                          AppConfigService appConfigService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
        this.appConfigService = appConfigService;
    }

    public int scrapeJobs() {
        // --- Load config ---
        String searchTerm = appConfigService.getValue("search_term");
        if (searchTerm == null || searchTerm.isEmpty()) {
            searchTerm = "java developer";
            logger.warn("search_term not found in AppConfig, using default fallback term: '{}'", searchTerm);
        }

        int maxScrapeLimit = 80;
        String maxLimitRaw = appConfigService.getValue("max_scrape_limit");
        try {
            if (maxLimitRaw != null && !maxLimitRaw.isEmpty()) {
                maxScrapeLimit = Integer.parseInt(maxLimitRaw);
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid max_scrape_limit value '{}', using default: {}", maxLimitRaw, maxScrapeLimit);
        }

        String lastKnownJobUrl = appConfigService.getValue("last_known_job_url");
        if (lastKnownJobUrl == null) {
            lastKnownJobUrl = "";
        }

        logger.info("Starting scrape for term: {}, max limit: {}, last known: {}",
                searchTerm, maxScrapeLimit, lastKnownJobUrl.isEmpty() ? "(none)" : lastKnownJobUrl);

        // --- Prepare request ---
        String encodedTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // --- Scraping loop ---
        int newJobsCount = 0;
        boolean foundKnownJob = false;
        String firstJobUrlOfFirstPage = null;

        outerLoop:
        for (int page = 1; ; page++) {
            int start = (page - 1) * PAGE_SIZE;
            String url = "https://job-search-api.jobs.ch/search/semantic?query=" + encodedTerm
                    + "&rows=" + PAGE_SIZE + "&start=" + start + "&sort=date";

            try {
                ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
                JsonNode root = response.getBody();

                if (root == null || !root.has("documents")) {
                    logger.info("Page {}: fetched 0 jobs – stopping", page);
                    break;
                }

                JsonNode results = root.get("documents");
                int pageSize = results.size();
                logger.info("Page {}: fetched {} jobs", page, pageSize);

                if (pageSize == 0) {
                    break;
                }

                int newOnThisPage = 0;
                for (int i = 0; i < pageSize; i++) {
                    JsonNode node = results.get(i);

                    String jobId = node.path("id").asText();
                    String jobUrl = "https://www.jobs.ch/en/vacancies/detail/" + jobId + "/";

                    // Track first job URL of the very first page for bookmark update
                    if (page == 1 && i == 0) {
                        firstJobUrlOfFirstPage = jobUrl;
                    }

                    // Stop condition: reached the previously known job
                    if (!lastKnownJobUrl.isEmpty() && jobUrl.equals(lastKnownJobUrl)) {
                        logger.info("Found known job at position {} on page {} – stopping", i + 1, page);
                        foundKnownJob = true;
                        break outerLoop;
                    }

                    // Stop condition: reached max limit
                    if (newJobsCount >= maxScrapeLimit) {
                        logger.info("Reached max limit of {} jobs – stopping", maxScrapeLimit);
                        break outerLoop;
                    }

                    if (jobUrl.isEmpty()) continue;

                    if (repository.findByUrl(jobUrl).isEmpty()) {
                        String title = node.path("title").asText();
                        String company = node.path("company").path("name").asText("Unknown Company");
                        String place = node.path("place").asText("Switzerland");

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
                        newOnThisPage++;
                    }
                }

                // Stop condition: full page had zero new jobs → we've reached already-known content
                if (newOnThisPage == 0 && pageSize == PAGE_SIZE) {
                    logger.info("Page {}: no new jobs found on full page – all content already seen, stopping", page);
                    break;
                }

            } catch (RestClientException e) {
                logger.error("Error fetching page {} for term '{}': {}", page, searchTerm, e.getMessage());
                break;
            }
        }

        // --- Update bookmark ---
        if (newJobsCount > 0 && firstJobUrlOfFirstPage != null) {
            appConfigService.setValue("last_known_job_url", firstJobUrlOfFirstPage);
            logger.info("Scraping finished: {} new jobs saved, last known URL updated to: {}",
                    newJobsCount, firstJobUrlOfFirstPage);
        } else {
            logger.info("Scraping finished: {} new jobs saved, last known URL not updated", newJobsCount);
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Fetch job description interrupted for: {}", url, e);
        } catch (IOException e) {
            logger.error("Failed to fetch job description from: {}", url, e);
        }
        return null;
    }

    private boolean isJobPosting(JsonNode node) {
        String type = node.path("@type").asText();
        return "JobPosting".equals(type);
    }
}
