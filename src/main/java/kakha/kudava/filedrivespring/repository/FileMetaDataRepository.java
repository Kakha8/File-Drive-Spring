package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.FileMetaData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileMetaDataRepository extends JpaRepository<FileMetaData, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update FileMetaData m
        set m.deleted = true
        where m.deleted = false
          and m.parent.prefix like concat(:prefix, '%')
    """)
    int softDeleteFilesByFolderPrefix(@Param("prefix") String prefix);

}
