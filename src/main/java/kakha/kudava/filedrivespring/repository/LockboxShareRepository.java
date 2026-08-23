package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.LockboxShare;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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

    boolean existsByShareUuid(UUID shareUuid);

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

    @Query("""
        select s
        from LockboxShare s
        join fetch s.lockboxFile lf
        join fetch lf.file metadata
        join fetch s.owner
        where s.recipient.id = :recipientId
          and s.status = :status
          and (s.expiresAt is null or s.expiresAt > :now)
          and metadata.deleted = false
          and metadata.permanentlyDeleted = false
        order by s.createdAt desc
        """)
    List<LockboxShare> findReceivedAvailableShares(
            @Param("recipientId") Long recipientId,
            @Param("status") LockboxShare.Status status,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("""
        select s
        from LockboxShare s
        join fetch s.lockboxFile lf
        join fetch lf.file metadata
        join fetch s.owner
        where s.shareUuid = :shareUuid
          and s.recipient.id = :recipientId
          and s.status = :status
          and (s.expiresAt is null or s.expiresAt > :now)
          and metadata.deleted = false
          and metadata.permanentlyDeleted = false
        """)
    Optional<LockboxShare> findReceivedAvailableShare(
            @Param("shareUuid") UUID shareUuid,
            @Param("recipientId") Long recipientId,
            @Param("status") LockboxShare.Status status,
            @Param("now") Instant now
    );


}
