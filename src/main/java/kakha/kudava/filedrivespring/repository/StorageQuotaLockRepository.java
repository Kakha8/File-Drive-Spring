package kakha.kudava.filedrivespring.repository;

import jakarta.persistence.LockModeType;
import kakha.kudava.filedrivespring.model.StorageQuotaLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StorageQuotaLockRepository extends JpaRepository<StorageQuotaLock, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from StorageQuotaLock q where q.id = :id")
    Optional<StorageQuotaLock> lockById(@Param("id") Long id);
}
