package kakha.kudava.filedrivespring.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "lockbox_files",
        indexes = {
                @Index(
                        name = "idx_lockbox_files_key_id",
                        columnList = "key_id"
                ),
                @Index(
                        name = "idx_lockbox_files_created_at",
                        columnList = "created_at"
                )
        }
)
public class LockboxFile {

    /*
     * The Lockbox record uses the same primary key as FileMetaData.
     *
     * This guarantees that one normal file record can have at most
     * one Lockbox metadata record.
     */
    @Id
    @Column(name = "file_id")
    private Long id;

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @MapsId
    @JoinColumn(
            name = "file_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(
                    name = "fk_lockbox_file_metadata"
            )
    )
    private FileMetaData file;

    /*
     * Version of .cseml/.fdcse container format.
     *
     * This is separate from the encryption algorithm version so
     * that the container layout can evolve independently.
     */
    @Column(
            name = "format_version",
            nullable = false
    )
    private int formatVersion = 1;

    /*
     * Identifies the complete cryptographic construction used by
     * the client. The server uses this only as metadata.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "algorithm_suite",
            nullable = false,
            length = 80
    )
    private AlgorithmSuite algorithmSuite =
            AlgorithmSuite.ML_KEM_1024_AES_256_GCM_V1;

    /*
     * Plaintext chunk size used by the encrypted container.
     *
     * This can help future clients understand the format without
     * relying only on hard-coded defaults.
     */
    @Column(
            name = "chunk_size",
            nullable = false
    )
    private int chunkSize;

    /*
     * Public identifier or fingerprint of the client encryption key.
     *
     * This must not contain the private key, recovery secret,
     * plaintext DEK, or any other secret.
     */
    @Column(
            name = "key_id",
            length = 128
    )
    private String keyId;

    /*
     * Optional client-encrypted metadata.
     *
     * This could eventually contain the encrypted original filename,
     * MIME type, plaintext size, or other private metadata.
     *
     * The server stores this blob but cannot decrypt it.
     */
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "encrypted_metadata")
    private byte[] encryptedMetadata;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    public LockboxFile(
            FileMetaData file,
            int formatVersion,
            AlgorithmSuite algorithmSuite,
            int chunkSize,
            String keyId
    ) {
        this.file = Objects.requireNonNull(
                file,
                "file"
        );

        if (formatVersion < 1) {
            throw new IllegalArgumentException(
                    "Format version must be at least 1."
            );
        }

        if (chunkSize < 1) {
            throw new IllegalArgumentException(
                    "Chunk size must be positive."
            );
        }

        this.formatVersion = formatVersion;
        this.algorithmSuite = Objects.requireNonNull(
                algorithmSuite,
                "algorithmSuite"
        );
        this.chunkSize = chunkSize;
        this.keyId = keyId;
    }

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum AlgorithmSuite {
        ML_KEM_1024_AES_256_GCM_V1
    }
}