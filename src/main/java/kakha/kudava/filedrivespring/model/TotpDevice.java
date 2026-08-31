package kakha.kudava.filedrivespring.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "totp_devices")
@Getter
@Setter
public class TotpDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Lob
    @Column(nullable = false)
    private byte[] encryptedSecret;

    @Column(nullable = false, length = 12)
    private byte[] encryptionNonce;

    @Column(nullable = false)
    private String encryptionKeyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant enrollmentExpiresAt;

    private Instant confirmedAt;

    private Long lastAcceptedCounter;

    @Version
    private Long version;

    public enum Status {
        PENDING,
        ACTIVE,
        REVOKED
    }
}
