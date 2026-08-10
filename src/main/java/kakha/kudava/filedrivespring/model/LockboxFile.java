package kakha.kudava.filedrivespring.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "lockbox_files",
        uniqueConstraints = @UniqueConstraint(name = "uk_lockbox_profile_client_revision",
                columnNames = {"profile_id", "client_file_id", "revision"}),
        indexes = @Index(name = "idx_lockbox_files_created_at", columnList = "created_at"))
public class LockboxFile {
    /* The shared PK means this row is the logical/current revision. A future
       LockboxFileRevision entity can hold revision history without changing FileMetaData. */
    @Id @Column(name = "file_id") private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId
    @JoinColumn(name = "file_id", nullable = false, unique = true)
    private FileMetaData file;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, updatable = false)
    private LockboxProfile profile;
    @Column(name = "client_file_id", nullable = false, updatable = false) private UUID clientFileId;
    @Column(name = "revision", nullable = false, updatable = false) private long revision;
    @Column(name = "format_version", nullable = false, updatable = false) private int formatVersion;
    @Column(name = "suite_id", nullable = false, updatable = false) private int suiteId;
    @Column(name = "container_size", nullable = false, updatable = false) private long containerSize;
    @Column(name = "container_hash", nullable = false, updatable = false, length = 64) private byte[] containerHash;
    @Column(name = "encryption_key_id", nullable = false, updatable = false, length = 32) private byte[] encryptionKeyId;
    @Column(name = "signing_key_id", nullable = false, updatable = false, length = 32) private byte[] signingKeyId;
    @Column(name = "device_uuid", nullable = false, updatable = false) private UUID deviceUuid;
    @Column(name = "chunk_size", nullable = false, updatable = false) private int chunkSize;
    @Column(name = "chunk_count", nullable = false, updatable = false) private long chunkCount;
    @Column(name = "container_object_key", nullable = false, unique = true, updatable = false, length = 500) private String containerObjectKey;
    @Column(name = "manifest_object_key", nullable = false, unique = true, updatable = false, length = 500) private String manifestObjectKey;
    @Column(name = "signature_object_key", nullable = false, unique = true, updatable = false, length = 500) private String signatureObjectKey;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public LockboxFile(FileMetaData file, LockboxProfile profile, UUID clientFileId, long revision,
                       int formatVersion, int suiteId, long containerSize, byte[] containerHash,
                       byte[] encryptionKeyId, byte[] signingKeyId, UUID deviceUuid,
                       int chunkSize, long chunkCount, String containerObjectKey,
                       String manifestObjectKey, String signatureObjectKey) {
        this.file = Objects.requireNonNull(file); this.profile = Objects.requireNonNull(profile);
        this.clientFileId = Objects.requireNonNull(clientFileId); this.revision = revision;
        this.formatVersion = formatVersion; this.suiteId = suiteId; this.containerSize = containerSize;
        this.containerHash = requireBytes(containerHash, 64); this.encryptionKeyId = requireBytes(encryptionKeyId, 32);
        this.signingKeyId = requireBytes(signingKeyId, 32); this.deviceUuid = Objects.requireNonNull(deviceUuid);
        this.chunkSize = chunkSize; this.chunkCount = chunkCount;
        this.containerObjectKey = requireText(containerObjectKey); this.manifestObjectKey = requireText(manifestObjectKey);
        this.signatureObjectKey = requireText(signatureObjectKey);
        validate();
    }

    public byte[] getContainerHash() { return containerHash.clone(); }
    public byte[] getEncryptionKeyId() { return encryptionKeyId.clone(); }
    public byte[] getSigningKeyId() { return signingKeyId.clone(); }
    @PrePersist void create() { validate(); Instant now = Instant.now(); if (createdAt == null) createdAt = now; updatedAt = now; }
    @PreUpdate void update() { validate(); updatedAt = Instant.now(); }
    private void validate() {
        if (revision < 1 || formatVersion != 3 || suiteId != 1 || containerSize < 0 || chunkSize < 1 || chunkCount < 1)
            throw new IllegalArgumentException("Invalid Lockbox v3 metadata");
        requireBytes(containerHash,64); requireBytes(encryptionKeyId,32); requireBytes(signingKeyId,32);
    }
    private static byte[] requireBytes(byte[] b,int n){ if(b==null||b.length!=n) throw new IllegalArgumentException("Invalid binary field"); return b.clone(); }
    private static String requireText(String s){ if(s==null||s.isBlank()) throw new IllegalArgumentException("Object key is required"); return s; }
}
