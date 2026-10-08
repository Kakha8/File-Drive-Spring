package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.QuarantinedFiles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

@Repository
public interface QuarantinedFilesRepository extends JpaRepository<QuarantinedFiles, Long>, JpaSpecificationExecutor<QuarantinedFiles> {
    @Query("select coalesce(sum(q.size), 0) from QuarantinedFiles q where q.deleted = false")
    long sumQuotaBytes();

    List<QuarantinedFiles> findAll();
    QuarantinedFiles findQuarantinedFileById(Long id);
    List<QuarantinedFiles> findByCreatedAtBeforeAndDeletedFalse(Instant cutoff);

    @Query("select distinct q.clamAvResponse from QuarantinedFiles q where q.deleted = false order by q.clamAvResponse")
    List<String> findActiveSignatures();
}
