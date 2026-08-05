package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.LockboxDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LockboxDeviceRepository extends JpaRepository<LockboxDevice, Long> {
    Optional<LockboxDeviceRepository> findByDeviceUuid(UUID deviceUuid);

    Optional<LockboxDeviceRepository> findByDeviceUuidAndProfileUserId(
            UUID deviceUuid,
            Long userId
    );

    List<LockboxDeviceRepository> findAllByProfileId(Long profileId);

    boolean existsByDeviceUuid(UUID deviceUuid);
}
