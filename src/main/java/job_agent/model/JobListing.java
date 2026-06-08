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
    private String status; // e.g., "new", "evaluated", "rejected", "accepted"
    private boolean isSeen;
    private Integer aiScore; // e.g., 1-100
    @Column(length = 1000) // Adjust length as needed
    private String aiReasoning;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isSeen() {
        return isSeen;
    }

    public void setSeen(boolean seen) {
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
               '}';
    }
}
