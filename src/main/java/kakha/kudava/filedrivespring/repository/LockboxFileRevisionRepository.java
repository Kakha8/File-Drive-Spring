package kakha.kudava.filedrivespring.repository;
import kakha.kudava.filedrivespring.model.LockboxFileRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List; import java.util.Optional;
public interface LockboxFileRevisionRepository extends JpaRepository<LockboxFileRevision,Long>{
    @Query("select coalesce(sum(coalesce(r.storedSize, r.containerSize)), 0) from LockboxFileRevision r")
    long sumQuotaBytes();
    Optional<LockboxFileRevision> findByLockboxFileIdAndRevision(Long fileId,long revision);
    List<LockboxFileRevision> findAllByLockboxFileIdOrderByRevisionDesc(Long fileId);
    boolean existsByLockboxFileIdAndRevision(Long fileId,long revision);
}
