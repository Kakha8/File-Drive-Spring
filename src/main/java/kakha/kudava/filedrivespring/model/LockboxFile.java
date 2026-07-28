package kakha.kudava.filedrivespring.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Getter
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
     * Shared primary key with FileMetaData.
     *
     * The LockboxFile ID is copied from the associated FileMetaData
     * record by @MapsId.
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
     * Version of the encrypted container layout.
     *
     * For your current CSEMLK02 container, this will be 2.
     * The value should come from LockboxContainerValidator.
     */
    @Column(
            name = "format_version",
            nullable = false
    )
    private int formatVersion;

    /*
     * Cryptographic construction used by the client.
     *
     * This is separate from the container format version.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "algorithm_suite",
            nullable = false,
            length = 80
    )
    private AlgorithmSuite algorithmSuite;

    /*
     * Plaintext chunk size declared by the encrypted container.
     */
    @Column(
            name = "chunk_size",
            nullable = false
    )
    private int chunkSize;

    /*
     * Public identifier or fingerprint of the encryption key.
     *
     * Must never contain private-key material, the plaintext DEK,
     * recovery secrets, or other sensitive key material.
     */
    @Column(
            name = "key_id",
            length = 128
    )
    private String keyId;

    /*
     * Optional metadata encrypted by the client.
     *
     * Lombok does not generate a getter for this field because byte
     * arrays are mutable. The custom getter returns a defensive copy.
     */
    @Getter(AccessLevel.NONE)
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

    /**
     * Creates Lockbox metadata without a separate encrypted metadata
     * blob.
     */
    public LockboxFile(
            FileMetaData file,
            int formatVersion,
            AlgorithmSuite algorithmSuite,
            int chunkSize,
            String keyId
    ) {
        this(
                file,
                formatVersion,
                algorithmSuite,
                chunkSize,
                keyId,
                null
        );
    }

    /**
     * Creates a complete Lockbox metadata record.
     */
    public LockboxFile(
            FileMetaData file,
            int formatVersion,
            AlgorithmSuite algorithmSuite,
            int chunkSize,
            String keyId,
            byte[] encryptedMetadata
    ) {
        this.file = Objects.requireNonNull(file, "file");
        this.formatVersion = requireValidFormatVersion(formatVersion);
        this.algorithmSuite = Objects.requireNonNull(
                algorithmSuite,
                "algorithmSuite"
        );
        this.chunkSize = requireValidChunkSize(chunkSize);
        this.keyId = requireValidKeyId(keyId);
        this.encryptedMetadata = copy(encryptedMetadata);
    }

    /**
     * Returns a copy so callers cannot modify the entity's internal
     * byte array directly.
     */
    public byte[] getEncryptedMetadata() {
        return copy(encryptedMetadata);
    }

    /**
     * Replaces the optional client-encrypted metadata.
     */
    public void updateEncryptedMetadata(byte[] encryptedMetadata) {
        this.encryptedMetadata = copy(encryptedMetadata);
    }

    /**
     * Removes the optional client-encrypted metadata.
     */
    public void clearEncryptedMetadata() {
        this.encryptedMetadata = null;
    }

    @PrePersist
    private void onCreate() {
        validateState();

        Instant now = Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        validateState();
        updatedAt = Instant.now();
    }

    private void validateState() {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(algorithmSuite, "algorithmSuite");

        requireValidFormatVersion(formatVersion);
        requireValidChunkSize(chunkSize);
        requireValidKeyId(keyId);
    }

    private static int requireValidFormatVersion(int formatVersion) {
        if (formatVersion < 1) {
            throw new IllegalArgumentException(
                    "Format version must be at least 1."
            );
        }

        return formatVersion;
    }

    private static int requireValidChunkSize(int chunkSize) {
        if (chunkSize < 1) {
            throw new IllegalArgumentException(
                    "Chunk size must be positive."
            );
        }

        return chunkSize;
    }

    private static String requireValidKeyId(String keyId) {
        if (keyId != null && keyId.length() > 128) {
            throw new IllegalArgumentException(
                    "Key ID must not exceed 128 characters."
            );
        }

        return keyId;
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : value.clone();
    }

    public enum AlgorithmSuite {
        ML_KEM_1024_AES_256_GCM_V1
    }
}