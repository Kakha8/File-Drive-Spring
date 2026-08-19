package kakha.kudava.filedrivespring.dto;

public record LockboxRecipientKeyResponse(
        String keyId,
        String algorithm,
        String publicKey
) {
}