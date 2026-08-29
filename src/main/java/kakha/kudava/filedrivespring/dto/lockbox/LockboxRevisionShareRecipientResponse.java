package kakha.kudava.filedrivespring.dto.lockbox;
import java.util.UUID;
public record LockboxRevisionShareRecipientResponse(String recipientUsername,UUID targetDeviceId,long expiresAtUnixSeconds){}
