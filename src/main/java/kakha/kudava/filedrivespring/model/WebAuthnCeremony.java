package kakha.kudava.filedrivespring.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webauthn_ceremonies", indexes = @Index(name = "idx_webauthn_ceremony_user", columnList = "user_id"))
@Getter @Setter
public class WebAuthnCeremony {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Kind kind;
    @Lob @Column(nullable = false) private String requestJson;
    @Lob private String authorizationJson;
    @Column(nullable = false, length = 64) private String passwordFingerprint;
    @Column(length = 64) private String loginTokenHash;
    @Column(length = 100) private String displayName;
    @Column(nullable = false) private boolean totpAuthorized;
    @Column(nullable = false) private Instant expiresAt;
    private Instant consumedAt;
    public enum Kind { REGISTRATION, LOGIN }
}
