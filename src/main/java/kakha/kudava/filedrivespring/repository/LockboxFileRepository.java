package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.LockboxFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LockboxFileRepository
        extends JpaRepository<LockboxFile, Long> {

    Optional<LockboxFile> findByFileId(Long fileId);

    boolean existsByFileId(Long fileId);

    boolean existsByProfileIdAndClientFileIdAndRevision(Long profileId, UUID clientFileId, long revision);

    Optional<LockboxFile> findByIdAndProfileUserId(Long id, Long userId);
    List<LockboxFile> findAllByProfileUserIdOrderByCreatedAtDesc(
            Long userId
    );
}
