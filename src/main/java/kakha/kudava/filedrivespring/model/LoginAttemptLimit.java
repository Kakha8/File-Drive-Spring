package kakha.kudava.filedrivespring.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** Serialized by the owning User's lock, shared by password and MFA attempts. */
@Entity
@Table(name = "login_attempt_limits")
@Getter
@Setter
public class LoginAttemptLimit {
    @Id private Long userId;
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false) private Instant windowStartedAt;
    @Column(nullable = false) private int failures;
    @Column(nullable = false) private int challenges;
}
