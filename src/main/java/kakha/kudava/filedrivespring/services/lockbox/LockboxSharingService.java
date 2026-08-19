package kakha.kudava.filedrivespring.services.lockbox;

import kakha.kudava.filedrivespring.dto.LockboxRecipientKeyResponse;
import kakha.kudava.filedrivespring.dto.LockboxRecipientKeysResponse;
import kakha.kudava.filedrivespring.exceptions.LockboxApiException;
import kakha.kudava.filedrivespring.model.LockboxDevice;
import kakha.kudava.filedrivespring.model.LockboxKey;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.LockboxKeyRepository;
import kakha.kudava.filedrivespring.repository.UserRepository;
import kakha.kudava.filedrivespring.services.ResourceAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Service
public class LockboxSharingService {

    private final UserRepository users;
    private final LockboxKeyRepository keys;
    private final ResourceAccessService access;

    public LockboxSharingService(
            UserRepository users,
            LockboxKeyRepository keys,
            ResourceAccessService access
    ) {
        this.users = Objects.requireNonNull(
                users,
                "users"
        );

        this.keys = Objects.requireNonNull(
                keys,
                "keys"
        );

        this.access = Objects.requireNonNull(
                access,
                "access"
        );
    }

    @Transactional(readOnly = true)
    public LockboxRecipientKeysResponse
    recipientEncryptionKeys(
            String username
    ) {
        User requester = access.currentUser();

        String normalizedUsername =
                normalizeUsername(username);

        User recipient = users
                .findByUsername(normalizedUsername)
                .orElseThrow(
                        LockboxSharingService::
                                recipientUnavailable
                );

        if (requester.getId().equals(
                recipient.getId()
        )) {
            throw new LockboxApiException(
                    "INVALID_SHARE_RECIPIENT",
                    HttpStatus.BAD_REQUEST,
                    "You cannot share a Lockbox file with yourself."
            );
        }

        List<LockboxKey> recipientKeys =
                keys.findAllByDeviceProfileUserIdAndDeviceStatusAndRoleAndStatus(
                        recipient.getId(),
                        LockboxDevice.Status.ACTIVE,
                        LockboxKey.Role.ENCRYPTION,
                        LockboxKey.Status.ACTIVE
                );

        if (recipientKeys.isEmpty()) {
            throw recipientUnavailable();
        }

        Base64.Encoder base64 =
                Base64.getEncoder();

        List<LockboxRecipientKeyResponse>
                keyResponses =
                recipientKeys.stream()
                        .map(key ->
                                new LockboxRecipientKeyResponse(
                                        base64.encodeToString(
                                                key.getKeyId()
                                        ),
                                        key.getAlgorithm().name(),
                                        base64.encodeToString(
                                                key.getPublicKey()
                                        )
                                )
                        )
                        .toList();

        return new LockboxRecipientKeysResponse(
                recipient.getId(),
                recipient.getUsername(),
                keyResponses
        );
    }

    private String normalizeUsername(
            String username
    ) {
        if (username == null) {
            throw recipientUnavailable();
        }

        String normalized =
                username.trim();

        if (normalized.isEmpty()
                || normalized.length() > 100) {
            throw recipientUnavailable();
        }

        return normalized;
    }

    private static LockboxApiException
    recipientUnavailable() {
        return new LockboxApiException(
                "LOCKBOX_RECIPIENT_UNAVAILABLE",
                HttpStatus.NOT_FOUND,
                "The Lockbox recipient is unavailable."
        );
    }
}