package kakha.kudava.filedrivespring.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Public credential data only. Registration must verify WebAuthn before persisting. */
@Entity
@Table(name = "webauthn_credentials",
        uniqueConstraints = @UniqueConstraint(name = "uk_webauthn_credential_id", columnNames = "credential_id"),
        indexes = @Index(name = "idx_webauthn_user_id", columnList = "user_id"))
@Getter
public class WebAuthnCredential {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_webauthn_user"))
    private User user;

    // Canonical, unpadded Base64URL. Requires case-sensitive database comparison.
    @NotBlank
    @Size(max = 1364)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$")
    @Column(name = "credential_id", nullable = false, length = 1364, updatable = false)
    private String credentialId;

    // Encoded COSE public key; format/algorithm verification belongs in enrollment.
    @NotBlank
    @Size(max = 2048)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$")
    @Column(name = "public_key_cose", nullable = false, length = 2048, updatable = false)
    private String publicKeyCose;

    @NotBlank
    @Size(max = 100)
    @Column(name = "display_name", nullable = false, length = 100)
    @Setter
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    @Setter
    private Instant lastUsedAt;

    protected WebAuthnCredential() {}

    public WebAuthnCredential(User user, String credentialId, String publicKeyCose, String displayName) {
        this.user = user;
        this.credentialId = credentialId;
        this.publicKeyCose = publicKeyCose;
        this.displayName = displayName;
    }

    @PrePersist
    private void beforeInsert() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
