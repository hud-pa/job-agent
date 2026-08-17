package job_agent.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "job_listings")
public class JobListing {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private String title;
    private String company;
    private String location;
    private String url;
    private String source;
    private LocalDate dateFound;
    @Column(columnDefinition = "TEXT")
    private String description;
    private String status; // e.g., "new", "evaluated", "rejected", "accepted"
    private Boolean isSeen = false;
    private Integer aiScore; // overall score 0-100
    @Column(length = 2000)
    private String aiReasoning;      // summary / reasoning text
    private Boolean aiEvaluated = false;

    // --- Structured sub-scores (added for detailed AI evaluation) ---
    private Integer technicalMatch;  // 0-100
    private Integer languageMatch;   // 0-100
    private Integer levelMatch;      // 0-100
    private Integer locationMatch;   // 0-100
    @Column(length = 1000)
    private String strengths;        // comma-separated, e.g. "Java, Spring Boot"
    @Column(length = 1000)
    private String missingSkills;    // comma-separated, e.g. "Docker, Kubernetes"
    private String recommendation;   // "APPLY", "CONSIDER" or "SKIP"

    public JobListing() {
    }

    public JobListing(String title, String company, String location, String url, String source, LocalDate dateFound) {
        this.title = title;
        this.company = company;
        this.location = location;
        this.url = url;
        this.source = source;
        this.dateFound = dateFound;
        this.status = "new";
        this.isSeen = false;
        this.aiEvaluated = false; // Default value for new listings
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDate getDateFound() {
        return dateFound;
    }

    public void setDateFound(LocalDate dateFound) {
        this.dateFound = dateFound;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean isSeen() {
        return isSeen;
    }

    public void setSeen(Boolean seen) {
        isSeen = seen;
    }

    public Integer getAiScore() {
        return aiScore;
    }

    public void setAiScore(Integer aiScore) {
        this.aiScore = aiScore;
    }

    public String getAiReasoning() {
        return aiReasoning;
    }

    public void setAiReasoning(String aiReasoning) {
        this.aiReasoning = aiReasoning;
    }

    public Boolean isAiEvaluated() {
        return aiEvaluated;
    }

    public void setAiEvaluated(Boolean aiEvaluated) {
        this.aiEvaluated = aiEvaluated;
    }

    public Integer getTechnicalMatch() {
        return technicalMatch;
    }

    public void setTechnicalMatch(Integer technicalMatch) {
        this.technicalMatch = technicalMatch;
    }

    public Integer getLanguageMatch() {
        return languageMatch;
    }

    public void setLanguageMatch(Integer languageMatch) {
        this.languageMatch = languageMatch;
    }

    public Integer getLevelMatch() {
        return levelMatch;
    }

    public void setLevelMatch(Integer levelMatch) {
        this.levelMatch = levelMatch;
    }

    public Integer getLocationMatch() {
        return locationMatch;
    }

    public void setLocationMatch(Integer locationMatch) {
        this.locationMatch = locationMatch;
    }

    public String getStrengths() {
        return strengths;
    }

    public void setStrengths(String strengths) {
        this.strengths = strengths;
    }

    public String getMissingSkills() {
        return missingSkills;
    }

    public void setMissingSkills(String missingSkills) {
        this.missingSkills = missingSkills;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    @Override
    public String toString() {
        return "JobListing{" +
               "id=" + id +
               ", title='" + title + '\'' +
               ", company='" + company + '\'' +
               ", location='" + location + '\'' +
               ", url='" + url + '\'' +
               ", source='" + source + '\'' +
               ", dateFound=" + dateFound +
               ", status='" + status + '\'' +
               ", isSeen=" + isSeen +
               ", aiScore=" + aiScore +
               ", aiReasoning='" + aiReasoning + '\'' +
               ", aiEvaluated=" + aiEvaluated +
               ", technicalMatch=" + technicalMatch +
               ", languageMatch=" + languageMatch +
               ", levelMatch=" + levelMatch +
               ", locationMatch=" + locationMatch +
               ", strengths='" + strengths + '\'' +
               ", missingSkills='" + missingSkills + '\'' +
               ", recommendation='" + recommendation + '\'' +
               '}';
    }
}
