package job_agent.repository;

import job_agent.model.JobListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobListingRepository extends JpaRepository<JobListing, UUID> {
    Optional<JobListing> findByUrl(String url);
    List<JobListing> findByStatus(String status);
}
