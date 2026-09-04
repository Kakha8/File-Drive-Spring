package kakha.kudava.filedrivespring.repository;

import io.minio.PutObjectArgs;
import kakha.kudava.filedrivespring.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.username = :username")
    Optional<User> findForTotpEnrollment(@Param("username") String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForAuthenticationUpdate(@Param("id") Long id);
    Optional<User> findByPublicUuid(UUID publicUuid);
    void deleteById(Long id);

    List<User> findByUsernameContainingIgnoreCase(String username);
}
