package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.TotpDevice;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
