package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.Folders;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends JpaRepository<Folders, Long> {

    Folders findFolderById(Long id);
    @NotNull List<Folders> findAll();

    @Query("select f.prefix from Folders f where f.id = :id")
    String findPrefixById(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Folders f
        set f.deleted = true
        where f.deleted = false
          and f.prefix like concat(:prefix, '%')
    """)
    int softDeleteTreeByPrefix(@Param("prefix") String prefix);}
