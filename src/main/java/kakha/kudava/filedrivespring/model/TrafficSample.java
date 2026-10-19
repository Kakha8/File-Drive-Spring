package kakha.kudava.filedrivespring.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "traffic_samples", indexes = {
        @Index(name = "idx_traffic_samples_occurred_at", columnList = "occurredAt"),
        @Index(name = "idx_traffic_samples_transfer_id", columnList = "transferId")
})
public class TrafficSample {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 48)
    private String transferId;

    @Column(nullable = false, length = 16)
    private String direction;

    @Column(nullable = false, length = 16)
    private String transferType;

    @Column(nullable = false)
    private long bytes;

    @Column(nullable = false)
    private Instant occurredAt;
}
