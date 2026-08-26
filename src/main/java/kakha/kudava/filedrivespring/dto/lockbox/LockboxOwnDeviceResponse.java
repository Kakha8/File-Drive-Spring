package kakha.kudava.filedrivespring.dto.lockbox;

import java.util.List;
import java.util.UUID;

public record LockboxOwnDeviceResponse(
        UUID deviceId,
        String deviceName,
        String deviceStatus,
        List<LockboxOwnDeviceKeyResponse> encryptionKeys
) {
    public LockboxOwnDeviceResponse {
        encryptionKeys = encryptionKeys == null ? List.of() : List.copyOf(encryptionKeys);
    }
}
