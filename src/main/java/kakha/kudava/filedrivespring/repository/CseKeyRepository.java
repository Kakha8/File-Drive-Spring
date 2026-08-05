package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.CseKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CseKeyRepository
        extends JpaRepository<CseKey, Long> {

    Optional<CseKey> findByKeyId(byte[] keyId);

    boolean existsByKeyId(byte[] keyId);

    List<CseKey> findAllByDeviceId(Long deviceId);

    Optional<CseKey> findByDeviceIdAndRoleAndStatus(
            Long deviceId,
            CseKey.Role role,
            CseKey.Status status
    );
}