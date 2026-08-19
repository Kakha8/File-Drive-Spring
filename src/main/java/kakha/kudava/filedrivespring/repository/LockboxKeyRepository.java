package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.LockboxDevice;
import kakha.kudava.filedrivespring.model.LockboxKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LockboxKeyRepository
        extends JpaRepository<LockboxKey, Long> {

    Optional<LockboxKey> findByKeyId(byte[] keyId);

    boolean existsByKeyId(byte[] keyId);

    List<LockboxKey> findAllByDeviceId(Long deviceId);

    Optional<LockboxKey> findByDeviceIdAndRoleAndStatus(
            Long deviceId,
            LockboxKey.Role role,
            LockboxKey.Status status
    );

    List<LockboxKey> findAllByDeviceProfileIdAndRoleAndStatus(
            Long profileId, LockboxKey.Role role, LockboxKey.Status status);

    List<LockboxKey>
    findAllByDeviceProfileUserIdAndDeviceStatusAndRoleAndStatus(
            Long userId,
            LockboxDevice.Status deviceStatus,
            LockboxKey.Role role,
            LockboxKey.Status keyStatus
    );
}
