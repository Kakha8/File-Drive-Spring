package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.SharingPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SharingPermissionRepository extends JpaRepository<SharingPermission, Long> {
}
