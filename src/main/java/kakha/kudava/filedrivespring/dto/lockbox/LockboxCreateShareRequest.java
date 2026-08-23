package kakha.kudava.filedrivespring.dto.lockbox;

public record LockboxCreateShareRequest(
        Long fileId,
        String envelope,
        String ownerSigningKeyId,
        String ownerSignature
) {
}
