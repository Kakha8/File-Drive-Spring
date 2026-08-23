package kakha.kudava.filedrivespring.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "lockbox_shares",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_lockbox_share_uuid",
                        columnNames = "share_uuid"
                )
        },
        indexes = {
                @Index(
                        name = "idx_lockbox_share_owner",
                        columnList = "owner_user_id"
                ),
                @Index(
                        name = "idx_lockbox_share_recipient_status",
                        columnList = "recipient_user_id, status"
                ),
                @Index(
                        name = "idx_lockbox_share_file",
                        columnList = "lockbox_file_id"
                )
        }
)
public class LockboxShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "share_uuid",
            nullable = false,
            updatable = false
    )
    private UUID shareUuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "lockbox_file_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(
                    name = "fk_lockbox_share_file"
            )
    )
    private LockboxFile lockboxFile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "owner_user_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(
                    name = "fk_lockbox_share_owner"
            )
    )
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "recipient_user_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(
                    name = "fk_lockbox_share_recipient"
            )
    )
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20
    )
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "permission",
            nullable = false,
            length = 20
    )
    private Permission permission;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public LockboxShare(
            UUID shareUuid,
            LockboxFile lockboxFile,
            User owner,
            User recipient,
            Permission permission,
            Instant expiresAt
    ) {
        this.shareUuid = Objects.requireNonNull(shareUuid, "shareUuid");

        this.lockboxFile = Objects.requireNonNull(
                lockboxFile,
                "lockboxFile"
        );

        this.owner = Objects.requireNonNull(
                owner,
                "owner"
        );

        this.recipient = Objects.requireNonNull(
                recipient,
                "recipient"
        );

        this.permission = Objects.requireNonNull(
                permission,
                "permission"
        );
        this.expiresAt = expiresAt;

        if (Objects.equals(
                owner.getId(),
                recipient.getId()
        )) {
            throw new IllegalArgumentException(
                    "A Lockbox file cannot be shared with its owner."
            );
        }

        status = Status.ACTIVE;
    }

    public void revoke() {
        if (status == Status.REVOKED) {
            return;
        }

        status = Status.REVOKED;
        revokedAt = Instant.now();
    }

    @PrePersist
    private void beforeInsert() {
        validate();

        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    private void beforeUpdate() {
        validate();
    }

    private void validate() {
        Objects.requireNonNull(
                shareUuid,
                "shareUuid"
        );

        Objects.requireNonNull(
                lockboxFile,
                "lockboxFile"
        );

        Objects.requireNonNull(
                owner,
                "owner"
        );

        Objects.requireNonNull(
                recipient,
                "recipient"
        );

        Objects.requireNonNull(
                status,
                "status"
        );

        Objects.requireNonNull(
                permission,
                "permission"
        );

        if (owner.getId() != null
                && owner.getId().equals(recipient.getId())) {
            throw new IllegalStateException(
                    "The owner cannot be the share recipient."
            );
        }
    }

    public enum Status {
        ACTIVE,
        REVOKED
    }

    public enum Permission {
        READ
    }
}
