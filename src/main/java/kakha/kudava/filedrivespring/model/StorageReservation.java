package kakha.kudava.filedrivespring.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "storage_reservations")
public class StorageReservation {
    @Id
    private UUID id;

    @Column(nullable = false)
    private long bytes;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected StorageReservation() {
    }

    public StorageReservation(UUID id, long bytes, Instant expiresAt) {
        this.id = id;
        this.bytes = bytes;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }
}
