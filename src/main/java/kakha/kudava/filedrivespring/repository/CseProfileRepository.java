package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.CseProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CseProfileRepository extends JpaRepository<CseProfile, Long> {
    Optional<CseProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
