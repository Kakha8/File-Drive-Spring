package kakha.kudava.filedrivespring.services.lockbox;

import kakha.kudava.filedrivespring.dto.LockboxEnrollmentChallengeResponse;
import kakha.kudava.filedrivespring.model.LockboxEnrollmentChallenge;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.LockboxEnrollmentChallengeRepository;
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

    public LockboxEnrollmentService(
            LockboxEnrollmentChallengeRepository
                    challengeRepository,
            LockboxProfileRepository profileRepository,
            ResourceAccessService access
    ) {
        this.challengeRepository = challengeRepository;
        this.profileRepository = profileRepository;
        this.access = access;
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
}