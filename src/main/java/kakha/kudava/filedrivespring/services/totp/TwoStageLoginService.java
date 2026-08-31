package kakha.kudava.filedrivespring.services.totp;

import kakha.kudava.filedrivespring.model.*;
import kakha.kudava.filedrivespring.repository.*;
import kakha.kudava.filedrivespring.security.TokenHashUtil;
import kakha.kudava.filedrivespring.services.jwt.AuthenticatedSession;
import kakha.kudava.filedrivespring.services.jwt.JwtRefreshService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class TwoStageLoginService {
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(3);
    private static final Duration LIMIT_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_FAILURES = 10, MAX_CHALLENGES = 10;
    private final UserRepository users;
    private final TotpDeviceRepository devices;
    private final MfaLoginChallengeRepository challenges;
    private final LoginAttemptLimitRepository limits;
    private final PasswordEncoder passwords;
    private final TotpSecretEncryptionService encryption;
    private final TotpVerificationService verifier;
    private final JwtRefreshService refresh;
    private final int refreshDays;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String dummyPasswordHash;

    @Autowired
    public TwoStageLoginService(UserRepository users, TotpDeviceRepository devices,
            MfaLoginChallengeRepository challenges, LoginAttemptLimitRepository limits,
            PasswordEncoder passwords, TotpSecretEncryptionService encryption,
            TotpVerificationService verifier, JwtRefreshService refresh,
            @Value("${JWT_REFRESH_DAYS}") int refreshDays) {
        this(users, devices, challenges, limits, passwords, encryption, verifier, refresh, refreshDays, Clock.systemUTC());
    }

    public TwoStageLoginService(UserRepository users, TotpDeviceRepository devices,
            MfaLoginChallengeRepository challenges, LoginAttemptLimitRepository limits,
            PasswordEncoder passwords, TotpSecretEncryptionService encryption,
            TotpVerificationService verifier, JwtRefreshService refresh, int refreshDays, Clock clock) {
        this.users = users; this.devices = devices; this.challenges = challenges; this.limits = limits;
        this.passwords = passwords; this.encryption = encryption; this.verifier = verifier;
        this.refresh = refresh; this.refreshDays = refreshDays; this.clock = clock;
        dummyPasswordHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = LoginRejected.class)
    public LoginResult login(String username, String password) {
        if (username == null || username.isBlank() || username.length() > 255
                || password == null || password.isEmpty() || password.length() > 1024) throw rejected();
        User user = users.findForTotpEnrollment(username).orElse(null);
        if (user == null) {
            passwords.matches(password, dummyPasswordHash);
            throw rejected();
        }
        Instant now = clock.instant();
        LoginAttemptLimit limit = limit(user, now);
        checkLimit(limit);
        if (!passwords.matches(password, user.getPassword())) fail(limit);
        if (user.getPublicUuid() == null) throw new IllegalStateException("Authenticated account has no public UUID.");
        if (!user.isTotpEnabled()) {
            challenges.consumeOutstanding(user.getId(), now);
            return new LoginResult(refresh.issueSession(user, refreshDays, false), null);
        }
        if (limit.getChallenges() >= MAX_CHALLENGES) throw throttled();
        if (devices.findAllByUserIdAndStatus(user.getId(), TotpDevice.Status.ACTIVE).isEmpty()) throw rejected();
        limit.setChallenges(limit.getChallenges() + 1);
        // A new successful password step replaces any previous outstanding challenge.
        challenges.consumeOutstanding(user.getId(), now);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Arrays.fill(bytes, (byte) 0);
        MfaLoginChallenge challenge = new MfaLoginChallenge();
        challenge.setTokenHash(TokenHashUtil.sha256(raw));
        challenge.setUser(user);
        challenge.setExpiresAt(now.plus(CHALLENGE_TTL));
        challenge.setPasswordFingerprint(TokenHashUtil.sha256(user.getPassword()));
        challenges.saveAndFlush(challenge);
        return new LoginResult(null, new MfaRequired(true, raw, challenge.getExpiresAt()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = LoginRejected.class)
    public AuthenticatedSession verify(String challengeToken, String code) {
        if (challengeToken == null || !challengeToken.matches("[A-Za-z0-9_-]{43}")) throw rejected();
        String hash = TokenHashUtil.sha256(challengeToken);
        Long userId = challenges.findOwnerId(hash).orElseThrow(TwoStageLoginService::rejected);
        User user = users.findForAuthenticationUpdate(userId).orElseThrow(TwoStageLoginService::rejected);
        Instant now = clock.instant();
        LoginAttemptLimit limit = limit(user, now);
        checkLimit(limit);
        MfaLoginChallenge challenge = challenges.findForUpdate(hash, userId).orElseThrow(TwoStageLoginService::rejected);
        if (challenge.getConsumedAt() != null) throw rejected();
        if (!now.isBefore(challenge.getExpiresAt()) || !user.isTotpEnabled()
                || !TokenHashUtil.sha256(user.getPassword()).equals(challenge.getPasswordFingerprint())) {
            challenge.setConsumedAt(now);
            throw rejected();
        }
        if (code == null || !code.matches("[0-9]{6}")) fail(limit);
        boolean matched = false;
        // The account lock serializes this with enrollment. Device locks also coordinate
        // counter consumption with any other OTP-verifying operation.
        var active = devices.findAllByUserIdAndStatus(userId, TotpDevice.Status.ACTIVE);
        active.sort(Comparator.comparing(TotpDevice::getId));
        for (TotpDevice candidate : active) {
            TotpDevice device = devices.findForEnrollmentUpdate(candidate.getId(), userId).orElseThrow(TwoStageLoginService::rejected);
            if (device.getStatus() != TotpDevice.Status.ACTIVE) continue;
            byte[] seed = encryption.decrypt(user.getPublicUuid(), device.getEncryptedSecret(),
                    device.getEncryptionNonce(), device.getEncryptionKeyId());
            try {
                OptionalLong counter = verifier.verify(seed, code, device.getLastAcceptedCounter());
                if (counter.isPresent()) {
                    device.setLastAcceptedCounter(counter.getAsLong());
                    matched = true;
                }
            } finally { Arrays.fill(seed, (byte) 0); }
        }
        if (!matched) fail(limit);
        challenge.setConsumedAt(now);
        return refresh.issueSession(user, refreshDays, true);
    }

    private LoginAttemptLimit limit(User user, Instant now) {
        LoginAttemptLimit result = limits.findById(user.getId()).orElseGet(() -> {
            LoginAttemptLimit created = new LoginAttemptLimit();
            created.setUser(user); created.setWindowStartedAt(now);
            return limits.saveAndFlush(created);
        });
        if (!now.isBefore(result.getWindowStartedAt().plus(LIMIT_WINDOW))) {
            result.setWindowStartedAt(now); result.setFailures(0); result.setChallenges(0);
        }
        return result;
    }

    private void checkLimit(LoginAttemptLimit limit) { if (limit.getFailures() >= MAX_FAILURES) throw throttled(); }
    private void fail(LoginAttemptLimit limit) { limit.setFailures(limit.getFailures() + 1); checkLimit(limit); throw rejected(); }
    private static LoginRejected rejected() { return new LoginRejected(HttpStatus.UNAUTHORIZED, "Invalid or expired authentication credentials."); }
    private static LoginRejected throttled() { return new LoginRejected(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Try again later."); }

    public static final class LoginRejected extends ResponseStatusException {
        private LoginRejected(HttpStatus status, String message) { super(status, message); }
    }
    public record MfaRequired(boolean mfaRequired, String challengeToken, Instant expiresAt) {
        @Override public String toString() { return "MfaRequired[redacted]"; }
    }
    public record LoginResult(AuthenticatedSession session, MfaRequired challenge) {
        @Override public String toString() { return "LoginResult[redacted]"; }
    }
}
