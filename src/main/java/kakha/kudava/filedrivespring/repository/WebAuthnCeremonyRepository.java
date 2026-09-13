package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.WebAuthnCeremony;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface WebAuthnCeremonyRepository extends JpaRepository<WebAuthnCeremony, UUID> {
    @Query("select c.user.id from WebAuthnCeremony c where c.id = :id")
    Optional<Long> findOwnerId(@Param("id") UUID id);
    // All writes are serialized by the owning User's pessimistic lock.
    void deleteAllByUserIdAndKind(Long userId, WebAuthnCeremony.Kind kind);
}
