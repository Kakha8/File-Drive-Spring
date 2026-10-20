package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.StorageReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface StorageReservationRepository extends JpaRepository<StorageReservation, UUID> {
    @Query("select coalesce(sum(r.bytes), 0) from StorageReservation r where r.expiresAt > :now")
    long sumActiveBytes(@Param("now") Instant now);

    @Modifying
    @Query("delete from StorageReservation r where r.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
