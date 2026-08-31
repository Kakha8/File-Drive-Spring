package kakha.kudava.filedrivespring.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** Access only while holding the owning User's write lock. */
@Entity
@Table(name = "totp_enrollment_limits")
@Getter
@Setter
public class TotpEnrollmentLimit {
    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant windowStartedAt;

    @Column(nullable = false)
    private int starts;

    @Column(nullable = false)
    private int failures;
}
