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
        name = "cse_devices",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_cse_devices_device_uuid",
                        columnNames = "device_uuid"
                )
        },
        indexes = {
                @Index(
                        name = "idx_cse_devices_profile_id",
                        columnList = "profile_id"
                ),
                @Index(
                        name = "idx_cse_devices_status",
                        columnList = "status"
                )
        }
)
public class LockboxDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "profile_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(
                    name = "fk_cse_devices_profile"
            )
    )
    private LockboxProfile profile;

    @Column(
            name = "device_uuid",
            nullable = false,
            updatable = false
    )
    private UUID deviceUuid;

    @Column(
            name = "display_name",
            nullable = false,
            length = 100
    )
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20
    )
    private Status status;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public LockboxDevice(
            LockboxProfile profile,
            UUID deviceUuid,
            String displayName
    ) {
        this.profile = Objects.requireNonNull(
                profile,
                "profile"
        );

        this.deviceUuid = Objects.requireNonNull(
                deviceUuid,
                "deviceUuid"
        );

        this.displayName = requireDisplayName(displayName);
        this.status = Status.ACTIVE;
    }

    public void activate() {
        if (status == Status.REVOKED) {
            throw new IllegalStateException(
                    "A revoked device cannot be activated."
            );
        }

        status = Status.ACTIVE;

        if (activatedAt == null) {
            activatedAt = Instant.now();
        }
    }

    public void markSeen() {
        if (status != Status.ACTIVE) {
            throw new IllegalStateException(
                    "Only an active device can be marked as seen."
            );
        }

        lastSeenAt = Instant.now();
    }

    public void rename(String displayName) {
        this.displayName = requireDisplayName(displayName);
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
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(deviceUuid, "deviceUuid");
        Objects.requireNonNull(status, "status");

        displayName = requireDisplayName(displayName);

        Instant now = Instant.now();

        createdAt = now;

        if (status == Status.ACTIVE && activatedAt == null) {
            activatedAt = now;
        }
    }

    @PreUpdate
    private void beforeUpdate() {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(deviceUuid, "deviceUuid");
        Objects.requireNonNull(status, "status");

        displayName = requireDisplayName(displayName);
    }

    private static String requireDisplayName(String value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Device display name is required."
            );
        }

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Device display name is required."
            );
        }

        if (normalized.length() > 100) {
            throw new IllegalArgumentException(
                    "Device display name cannot exceed 100 characters."
            );
        }

        return normalized;
    }

    public enum Status {
        PENDING,
        ACTIVE,
        REVOKED
    }
}