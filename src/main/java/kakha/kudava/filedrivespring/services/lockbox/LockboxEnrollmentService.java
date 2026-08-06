package kakha.kudava.filedrivespring.services.lockbox;

import kakha.kudava.filedrivespring.dto.LockboxEnrollmentChallengeResponse;
import kakha.kudava.filedrivespring.dto.LockboxEnrollmentCompleteRequest;
import kakha.kudava.filedrivespring.dto.LockboxEnrollmentCompleteResponse;
import kakha.kudava.filedrivespring.model.*;
import kakha.kudava.filedrivespring.repository.LockboxDeviceRepository;
import kakha.kudava.filedrivespring.repository.LockboxEnrollmentChallengeRepository;
import kakha.kudava.filedrivespring.repository.LockboxKeyRepository;
import kakha.kudava.filedrivespring.repository.LockboxProfileRepository;
import kakha.kudava.filedrivespring.services.ResourceAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class LockboxEnrollmentService {

    private static final int CHALLENGE_LENGTH = 32;

    private static final Duration CHALLENGE_LIFETIME =
            Duration.ofMinutes(5);

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    private final LockboxEnrollmentChallengeRepository
            challengeRepository;

    private final LockboxProfileRepository profileRepository;

    private final ResourceAccessService access;
    private final LockboxDeviceRepository deviceRepository;
    private final LockboxKeyRepository keyRepository;
    private final LockboxEnrollmentTranscript transcriptEncoder;
    private final LockboxSignatureVerifier signatureVerifier;

    private static final int KEY_ID_LENGTH = 32;
    private static final int ML_KEM_1024_PUBLIC_KEY_LENGTH = 1_568;
    private static final int ML_DSA_87_PUBLIC_KEY_LENGTH = 2_592;
    private static final int ML_DSA_87_SIGNATURE_LENGTH = 4_627;

    public LockboxEnrollmentService(
            LockboxEnrollmentChallengeRepository
                    challengeRepository,
            LockboxProfileRepository profileRepository,
            ResourceAccessService access, LockboxDeviceRepository deviceRepository, LockboxKeyRepository keyRepository, LockboxEnrollmentTranscript transcriptEncoder, LockboxSignatureVerifier signatureVerifier
    ) {
        this.challengeRepository = challengeRepository;
        this.profileRepository = profileRepository;
        this.access = access;
        this.deviceRepository = deviceRepository;
        this.keyRepository = keyRepository;
        this.transcriptEncoder = transcriptEncoder;
        this.signatureVerifier = signatureVerifier;
    }

    /**
     * Starts first-device Lockbox enrollment.
     *
     * The raw challenge is returned once. Only its SHA3-256 hash
     * is persisted.
     */
    @Transactional
    public LockboxEnrollmentChallengeResponse beginEnrollment() {
        User user = access.currentUser();

        rejectAlreadyEnabledAccount(user);
        cancelExistingPendingChallenges(user);

        byte[] challenge = generateChallenge();
        byte[] challengeHash = sha3_256(challenge);

        UUID enrollmentId = UUID.randomUUID();

        Instant now = Instant.now();
        Instant expiresAt =
                now.plus(CHALLENGE_LIFETIME);

        LockboxEnrollmentChallenge enrollment =
                new LockboxEnrollmentChallenge(
                        user,
                        enrollmentId,
                        challengeHash,
                        expiresAt
                );

        challengeRepository.saveAndFlush(enrollment);

        String encodedChallenge =
                Base64.getEncoder()
                        .encodeToString(challenge);

        return new LockboxEnrollmentChallengeResponse(
                enrollmentId,
                encodedChallenge,
                expiresAt
        );
    }

    private void rejectAlreadyEnabledAccount(User user) {
        if (profileRepository.existsByUserId(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Lockbox is already enabled for this account."
            );
        }
    }

    private void cancelExistingPendingChallenges(User user) {
        List<LockboxEnrollmentChallenge> pending =
                challengeRepository
                        .findAllByUserIdAndStatus(
                                user.getId(),
                                LockboxEnrollmentChallenge.Status.PENDING
                        );

        for (LockboxEnrollmentChallenge challenge : pending) {
            challenge.cancel();
        }

        /*
         * Explicit save is not strictly required for managed entities,
         * but makes the intended state transition clear.
         */
        challengeRepository.saveAll(pending);
    }

    private byte[] generateChallenge() {
        byte[] challenge =
                new byte[CHALLENGE_LENGTH];

        SECURE_RANDOM.nextBytes(challenge);

        return challenge;
    }

    private byte[] sha3_256(byte[] input) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA3-256");

            return digest.digest(input);
        } catch (NoSuchAlgorithmException exception) {
            /*
             * SHA3-256 is required by the Java platform versions used
             * by modern Spring Boot. Failure means the runtime cannot
             * support the Lockbox protocol.
             */
            throw new IllegalStateException(
                    "SHA3-256 is unavailable.",
                    exception
            );
        }
    }


    @Transactional
    public LockboxEnrollmentCompleteResponse completeEnrollment(
            UUID enrollmentId,
            LockboxEnrollmentCompleteRequest request
    ) {
        Objects.requireNonNull(
                enrollmentId,
                "enrollmentId"
        );

        if (request == null) {
            throw badRequest(
                    "Enrollment completion request is required."
            );
        }

        User user = access.currentUser();

        /*
         * The locking repository query prevents two concurrent completion
         * requests from consuming the same enrollment.
         */
        LockboxEnrollmentChallenge enrollment =
                challengeRepository
                        .findForCompletion(
                                enrollmentId,
                                user.getId()
                        )
                        .orElseThrow(() -> badRequest(
                                "Lockbox enrollment was not found."
                        ));

        requirePendingEnrollment(enrollment);
        requireUnexpiredEnrollment(enrollment);

        if (profileRepository.existsByUserId(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Lockbox is already enabled for this account."
            );
        }

        byte[] challenge = decodeBase64Exact(
                request.challenge(),
                CHALLENGE_LENGTH,
                "challenge"
        );

        verifyChallenge(enrollment, challenge);

        DecodedEnrollmentKeys keys =
                decodeEnrollmentKeys(request);

        verifyKeyIds(keys);
        verifyUniqueness(request.deviceId(), keys);

        byte[] signature = decodeBase64Exact(
                request.signature(),
                ML_DSA_87_SIGNATURE_LENGTH,
                "signature"
        );

        /*
         * Both Rust and Java must encode precisely the same transcript.
         * The backend reconstructs it rather than trusting bytes supplied
         * by the client.
         */
        byte[] transcript = transcriptEncoder.encode(
                enrollment.getEnrollmentId(),
                challenge,
                enrollment.getExpiresAt(),
                requireDeviceId(request.deviceId()),
                requireDeviceName(request.deviceName()),
                keys.encryptionKeyId(),
                keys.encryptionPublicKey(),
                keys.signingKeyId(),
                keys.signingPublicKey()
        );

        signatureVerifier.verify(
                keys.signingPublicKey(),
                transcript,
                signature
        );

        /*
         * No persistent identity records are created until all
         * cryptographic checks above succeed.
         */
        LockboxProfile profile =
                profileRepository.save(
                        new LockboxProfile(user)
                );

        LockboxDevice device =
                deviceRepository.save(
                        new LockboxDevice(
                                profile,
                                request.deviceId(),
                                request.deviceName()
                        )
                );

        LockboxKey encryptionKey =
                new LockboxKey(
                        device,
                        LockboxKey.Role.ENCRYPTION,
                        LockboxKey.Algorithm.ML_KEM_1024,
                        keys.encryptionKeyId(),
                        keys.encryptionPublicKey()
                );

        LockboxKey signingKey =
                new LockboxKey(
                        device,
                        LockboxKey.Role.SIGNING,
                        LockboxKey.Algorithm.ML_DSA_87,
                        keys.signingKeyId(),
                        keys.signingPublicKey()
                );

        keyRepository.save(encryptionKey);
        keyRepository.save(signingKey);

        enrollment.consume(Instant.now());

        /*
         * Flush inside the transaction so constraint violations are
         * detected before a successful response is returned.
         */
        keyRepository.flush();

        return new LockboxEnrollmentCompleteResponse(
                LockboxEnrollmentCompleteResponse
                        .LockboxProfileStatus.ENABLED,
                LockboxEnrollmentCompleteResponse
                        .LockboxDeviceStatus.ACTIVE,
                device.getDeviceUuid(),
                encodeBase64(encryptionKey.getKeyId()),
                encodeBase64(signingKey.getKeyId())
        );
    }

    private void requirePendingEnrollment(
            LockboxEnrollmentChallenge enrollment
    ) {
        if (enrollment.getStatus()
                != LockboxEnrollmentChallenge.Status.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Lockbox enrollment is no longer pending."
            );
        }
    }

    private void requireUnexpiredEnrollment(
            LockboxEnrollmentChallenge enrollment
    ) {
        if (enrollment.isExpired(Instant.now())) {
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "Lockbox enrollment has expired."
            );
        }
    }

    private void verifyChallenge(
            LockboxEnrollmentChallenge enrollment,
            byte[] challenge
    ) {
        byte[] calculatedHash = sha3_256(challenge);

        boolean matches = MessageDigest.isEqual(
                enrollment.getChallengeHash(),
                calculatedHash
        );

        if (!matches) {
            throw badRequest(
                    "Enrollment challenge is invalid."
            );
        }
    }

    private DecodedEnrollmentKeys decodeEnrollmentKeys(
            LockboxEnrollmentCompleteRequest request
    ) {
        LockboxEnrollmentCompleteRequest.PublicKeyRequest
                encryption = request.encryptionKey();

        LockboxEnrollmentCompleteRequest.PublicKeyRequest
                signing = request.signingKey();

        if (encryption == null || signing == null) {
            throw badRequest(
                    "Both encryption and signing keys are required."
            );
        }

        if (!"ML_KEM_1024".equals(encryption.algorithm())) {
            throw badRequest(
                    "Unsupported encryption-key algorithm."
            );
        }

        if (!"ML_DSA_87".equals(signing.algorithm())) {
            throw badRequest(
                    "Unsupported signing-key algorithm."
            );
        }

        return new DecodedEnrollmentKeys(
                decodeBase64Exact(
                        encryption.keyId(),
                        KEY_ID_LENGTH,
                        "encryptionKey.keyId"
                ),
                decodeBase64Exact(
                        encryption.publicKey(),
                        ML_KEM_1024_PUBLIC_KEY_LENGTH,
                        "encryptionKey.publicKey"
                ),
                decodeBase64Exact(
                        signing.keyId(),
                        KEY_ID_LENGTH,
                        "signingKey.keyId"
                ),
                decodeBase64Exact(
                        signing.publicKey(),
                        ML_DSA_87_PUBLIC_KEY_LENGTH,
                        "signingKey.publicKey"
                )
        );
    }

    private void verifyKeyIds(DecodedEnrollmentKeys keys) {
        byte[] expectedEncryptionKeyId =
                sha3_256(keys.encryptionPublicKey());

        if (!MessageDigest.isEqual(
                expectedEncryptionKeyId,
                keys.encryptionKeyId()
        )) {
            throw badRequest(
                    "Encryption key ID does not match its public key."
            );
        }

        byte[] expectedSigningKeyId =
                sha3_256(keys.signingPublicKey());

        if (!MessageDigest.isEqual(
                expectedSigningKeyId,
                keys.signingKeyId()
        )) {
            throw badRequest(
                    "Signing key ID does not match its public key."
            );
        }
    }

    private void verifyUniqueness(
            UUID deviceId,
            DecodedEnrollmentKeys keys
    ) {
        UUID checkedDeviceId = requireDeviceId(deviceId);

        if (deviceRepository.existsByDeviceUuid(
                checkedDeviceId
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This Lockbox device is already registered."
            );
        }

        if (keyRepository.existsByKeyId(
                keys.encryptionKeyId()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The encryption key is already registered."
            );
        }

        if (keyRepository.existsByKeyId(
                keys.signingKeyId()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The signing key is already registered."
            );
        }
    }

    private UUID requireDeviceId(UUID deviceId) {
        if (deviceId == null) {
            throw badRequest("Device ID is required.");
        }

        return deviceId;
    }

    private String requireDeviceName(String deviceName) {
        if (deviceName == null
                || deviceName.isBlank()) {
            throw badRequest(
                    "Device name is required."
            );
        }

        String normalized = deviceName.trim();

        if (normalized.length() > 100) {
            throw badRequest(
                    "Device name cannot exceed 100 characters."
            );
        }

        return normalized;
    }

    private byte[] decodeBase64Exact(
            String value,
            int expectedLength,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw badRequest(
                    fieldName + " is required."
            );
        }

        final byte[] decoded;

        try {
            decoded = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw badRequest(
                    fieldName + " is not valid Base64."
            );
        }

        if (decoded.length != expectedLength) {
            throw badRequest(
                    fieldName
                            + " must decode to exactly "
                            + expectedLength
                            + " bytes."
            );
        }

        return decoded;
    }

    private String encodeBase64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private ResponseStatusException badRequest(
            String message
    ) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    private record DecodedEnrollmentKeys(
            byte[] encryptionKeyId,
            byte[] encryptionPublicKey,
            byte[] signingKeyId,
            byte[] signingPublicKey
    ) {
        private DecodedEnrollmentKeys {
            encryptionKeyId = encryptionKeyId.clone();
            encryptionPublicKey = encryptionPublicKey.clone();
            signingKeyId = signingKeyId.clone();
            signingPublicKey = signingPublicKey.clone();
        }

        @Override
        public byte[] encryptionKeyId() {
            return encryptionKeyId.clone();
        }

        @Override
        public byte[] encryptionPublicKey() {
            return encryptionPublicKey.clone();
        }

        @Override
        public byte[] signingKeyId() {
            return signingKeyId.clone();
        }

        @Override
        public byte[] signingPublicKey() {
            return signingPublicKey.clone();
        }
    }



}