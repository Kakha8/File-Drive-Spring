package kakha.kudava.filedrivespring.dto;

import java.util.List;

public record LockboxRecipientKeysResponse(
        Long recipientId,
        String username,
        List<LockboxRecipientKeyResponse> encryptionKeys
) {
}