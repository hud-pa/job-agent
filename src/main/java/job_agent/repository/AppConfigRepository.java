package job_agent.repository;

import job_agent.model.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {
    Optional<AppConfig> findByConfigKey(String configKey);
}
