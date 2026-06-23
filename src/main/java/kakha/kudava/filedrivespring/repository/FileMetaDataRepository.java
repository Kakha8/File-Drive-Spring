package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.FileMetaData;
import kakha.kudava.filedrivespring.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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

    List<FileMetaData> findByParent_Id(Long parentId);
    @Query("""
    select f.parent.id
    from FileMetaData f
    where f.objectKey = :objectKey
""")
    Long findParentIdByObjectKey(@Param("objectKey") String objectKey);

    List<FileMetaData> findByObjectKeyStartingWithAndDeletedFalse(String prefix);
    Optional<FileMetaData> findByObjectKey(String objectKey);

    List<FileMetaData> findAllByParentId(Long parentId);

    List<FileMetaData> findByParentId(Long id);

    List<FileMetaData> findByParent_IdAndDeletedFalse(Long parentId);
    List<FileMetaData> findByParent_OwnerAndDeletedTrue(User user);
    Optional<FileMetaData> findByIdAndDeletedTrueAndParent_Owner(Long id, User owner);

    List<FileMetaData> findByParent_OwnerAndDeletedTrueAndOriginalObjectKeyStartingWith(
            User owner,
            String originalObjectKey
    );


    boolean existsByObjectKeyAndDeletedFalseAndPermanentlyDeletedFalse(String objectKey);

    List<FileMetaData> findByParent_OwnerAndDeletedTrueAndPermanentlyDeletedFalse(User owner);

    Optional<FileMetaData> findByIdAndDeletedTrueAndPermanentlyDeletedFalseAndParent_Owner(
            Long id,
            User owner
    );

    List<FileMetaData> findByParent_OwnerAndDeletedTrueAndPermanentlyDeletedFalseAndOriginalObjectKeyStartingWith(
            User owner,
            String originalObjectKey
    );

    List<FileMetaData> findByDeletedTrueAndPermanentlyDeletedFalseAndDeletedAtBefore(
            Instant cutoff
    );
}
