package kakha.kudava.filedrivespring.model;

import jakarta.persistence.*;
import kakha.kudava.filedrivespring.enums.EntityType;
import kakha.kudava.filedrivespring.enums.NotificationType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "notifications",
        indexes = {
                @Index(
                        name = "idx_notifications_recipient_created",
                        columnList = "recipient_id, created_at"
                ),
                @Index(
                        name = "idx_notifications_recipient_unread",
                        columnList = "recipient_id, read_at, removed_at"
                ),
                @Index(
                        name = "idx_notifications_target",
                        columnList = "entity_type, entity_id"
                )
        }
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user who receives and owns this notification.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    /**
     * The user whose action caused the notification.
     * Null for system-generated notifications such as storage warnings.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * Optional reference to the related file, folder, or bulk operation.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 20)
    private EntityType entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    /**
     * Soft deletion keeps notification history available for auditing.
     */
    @Column(name = "removed_at")
    private Instant removedAt;

    public Notification(
            User recipient,
            User actor,
            NotificationType type,
            String title,
            String message,
            EntityType entityType,
            Long entityId
    ) {
        this.recipient = Objects.requireNonNull(
                recipient,
                "recipient is required"
        );

        this.actor = actor;

        this.type = Objects.requireNonNull(
                type,
                "type is required"
        );

        this.title = requireText(
                title,
                "title",
                160
        );

        this.message = requireText(
                message,
                "message",
                null
        );

        this.entityType = entityType;
        this.entityId = entityId;

        if ((entityType == null) != (entityId == null)) {
            throw new IllegalArgumentException(
                    "entityType and entityId must either both be set or both be null"
            );
        }
    }

    @PrePersist
    private void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    public boolean isActive() {
        return removedAt == null;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }

    public void markUnread() {
        readAt = null;
    }

    public void remove() {
        if (removedAt == null) {
            removedAt = Instant.now();
        }
    }

    public void restore() {
        removedAt = null;
    }

    private static String requireText(
            String value,
            String fieldName,
            Integer maximumLength
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required"
            );
        }

        String trimmed = value.trim();

        if (
                maximumLength != null &&
                        trimmed.length() > maximumLength
        ) {
            throw new IllegalArgumentException(
                    fieldName +
                            " must not exceed " +
                            maximumLength +
                            " characters"
            );
        }

        return trimmed;
    }
}