package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.Folders;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends JpaRepository<Folders, Long> {

    Optional<Folders> findFolderById(Long id);
    @NotNull List<Folders> findAll();

}
