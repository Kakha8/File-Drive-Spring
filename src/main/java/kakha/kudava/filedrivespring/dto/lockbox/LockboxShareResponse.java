package kakha.kudava.filedrivespring.dto.lockbox;

public record LockboxShareResponse(
        String shareId,
        Long fileId,
        String ownerUsername,
        String recipientUsername,
        String recipientKeyId,
        String status
) {
}