package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.TotpDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface TotpDeviceRepository
        extends JpaRepository<TotpDevice, Long> {

    List<TotpDevice> findAllByUserIdAndStatus(
            Long userId,
            TotpDevice.Status status
    );

    Optional<TotpDevice> findByIdAndUserId(
            Long id,
            Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from TotpDevice d where d.id = :deviceId and d.user.id = :userId")
    Optional<TotpDevice> findForEnrollmentUpdate(
            @Param("deviceId") Long deviceId, @Param("userId") Long userId);
}
