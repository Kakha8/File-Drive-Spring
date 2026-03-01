package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.FileMetaData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileMetaDataRepository extends JpaRepository<FileMetaData, Long> {
    FileMetaData getFileMetaDataById(Long id);
    void deleteFileMetaDataById(Long id);
}
