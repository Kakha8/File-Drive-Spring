package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.LoginAttemptLimit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAttemptLimitRepository extends JpaRepository<LoginAttemptLimit, Long> {}
