package kakha.kudava.filedrivespring.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "mfa_login_challenges", indexes = @Index(name = "idx_mfa_challenge_user", columnList = "user_id"))
@Getter
@Setter
public class MfaLoginChallenge {
    @Id
    @Column(length = 64)
    private String tokenHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant consumedAt;
    // Hash of the encoded password, used only to invalidate challenges after password changes.
    @Column(nullable = false, length = 64)
    private String passwordFingerprint;
}
