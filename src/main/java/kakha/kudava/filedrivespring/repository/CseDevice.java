package kakha.kudava.filedrivespring.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CseDevice extends JpaRepository<CseDevice, Long> {
    Optional<CseDevice> findByDeviceUuid(UUID deviceUuid);

    Optional<CseDevice> findByDeviceUuidAndProfileUserId(
            UUID deviceUuid,
            Long userId
    );

    List<CseDevice> findAllByProfileId(Long profileId);

    boolean existsByDeviceUuid(UUID deviceUuid);
}
