package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.LockboxDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LockboxDeviceRepository extends JpaRepository<LockboxDevice, Long> {
    Optional<LockboxDevice> findByDeviceUuid(
            UUID deviceUuid
    );

    Optional<LockboxDevice>
    findByDeviceUuidAndProfileUserId(
            UUID deviceUuid,
            Long userId
    );

    List<LockboxDevice> findAllByProfileId(
            Long profileId
    );

    Optional<LockboxDevice> findByProfileIdAndDeviceUuid(
            Long profileId,
            UUID deviceUuid
    );
    boolean existsByDeviceUuid(UUID deviceUuid);
}
