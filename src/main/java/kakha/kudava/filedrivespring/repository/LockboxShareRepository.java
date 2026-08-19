package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.LockboxShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LockboxShareRepository
        extends JpaRepository<LockboxShare, Long> {

    Optional<LockboxShare> findByShareUuid(
            UUID shareUuid
    );

    Optional<LockboxShare>
    findByShareUuidAndOwnerId(
            UUID shareUuid,
            Long ownerId
    );

    Optional<LockboxShare>
    findByShareUuidAndRecipientId(
            UUID shareUuid,
            Long recipientId
    );

    Optional<LockboxShare>
    findByIdAndOwnerId(
            Long shareId,
            Long ownerId
    );

    Optional<LockboxShare>
    findByIdAndRecipientId(
            Long shareId,
            Long recipientId
    );

    boolean existsByLockboxFileIdAndRecipientIdAndStatusIn(
            Long lockboxFileId,
            Long recipientId,
            Collection<LockboxShare.Status> statuses
    );

    List<LockboxShare>
    findAllByRecipientIdAndStatusOrderByCreatedAtDesc(
            Long recipientId,
            LockboxShare.Status status
    );

    List<LockboxShare>
    findAllByRecipientIdAndStatusInOrderByCreatedAtDesc(
            Long recipientId,
            Collection<LockboxShare.Status> statuses
    );

    List<LockboxShare>
    findAllByOwnerIdOrderByCreatedAtDesc(
            Long ownerId
    );

    List<LockboxShare>
    findAllByLockboxFileIdOrderByCreatedAtDesc(
            Long lockboxFileId
    );
}