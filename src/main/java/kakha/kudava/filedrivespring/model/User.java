package kakha.kudava.filedrivespring.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "public_uuid",
            nullable = false,
            unique = true,
            updatable = false
    )
    private UUID publicUuid;

    @Column(nullable = false, unique = true)
    private String username;
    @Column(nullable = false)
    private String password;
    @Enumerated(EnumType.STRING)
    private Role role;

    public enum Role {
        USER, ADMIN
    }

    @Column(nullable = false)
    private boolean totpEnabled = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean webauthnEnabled = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean passwordChangeRequired = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean suspended = false;

    @Column
    private Instant suspendedAt;

    @Column
    private Instant suspendedUntil;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted = false;

    @Column
    private Instant deletedAt;

    @Column(updatable = false)
    private Instant createdAt;

    @Transient
    public boolean isSuspensionActive() {
        return !deleted && suspended && (suspendedUntil == null || suspendedUntil.isAfter(Instant.now()));
    }

    @PrePersist
    private void beforeInsert() {
        if (publicUuid == null) {
            publicUuid = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
