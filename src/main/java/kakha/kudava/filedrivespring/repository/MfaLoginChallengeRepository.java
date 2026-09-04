package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.MfaLoginChallenge;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;

public interface MfaLoginChallengeRepository extends JpaRepository<MfaLoginChallenge, String> {
    // Scalar lookup avoids loading a stale User before its lock is acquired.
    @Query("select c.user.id from MfaLoginChallenge c where c.tokenHash = :hash")
    Optional<Long> findOwnerId(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from MfaLoginChallenge c where c.tokenHash = :hash and c.user.id = :userId")
    Optional<MfaLoginChallenge> findForUpdate(@Param("hash") String hash, @Param("userId") Long userId);

    @Modifying
    @Query("update MfaLoginChallenge c set c.consumedAt = :now where c.user.id = :userId and c.consumedAt is null")
    int consumeOutstanding(@Param("userId") Long userId, @Param("now") Instant now);
}
