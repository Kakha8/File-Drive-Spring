package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.LockboxEnrollmentChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LockboxEnrollmentChallengeRepository
        extends JpaRepository<LockboxEnrollmentChallenge, Long> {
    Optional<LockboxEnrollmentChallenge>
    findByEnrollmentId(UUID enrollmentId);

    Optional<LockboxEnrollmentChallenge>
    findByEnrollmentIdAndUserId(
            UUID enrollmentId,
            Long userId
    );

    List<LockboxEnrollmentChallenge>
    findAllByUserIdAndStatus(
            Long userId,
            LockboxEnrollmentChallenge.Status status
    );
}
