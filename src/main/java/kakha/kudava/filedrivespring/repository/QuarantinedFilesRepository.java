package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.QuarantinedFiles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuarantinedFilesRepository extends JpaRepository<QuarantinedFiles, Long> {
    List<QuarantinedFiles> findAll();
    QuarantinedFiles findQuarantinedFileById(Long id);
}
