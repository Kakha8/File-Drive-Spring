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
    private final MfaLoginChallengeRepository challenges;
    private final LoginAttemptLimitRepository limits;
    private final PasswordEncoder passwords;
    private final JwtRefreshService refresh;
    private final int refreshDays;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String dummyPasswordHash;

    @Autowired
    public TwoStageLoginService(UserRepository users,
            MfaLoginChallengeRepository challenges, LoginAttemptLimitRepository limits,
            PasswordEncoder passwords, JwtRefreshService refresh,
            @Value("${JWT_REFRESH_DAYS}") int refreshDays) {
        this(users, challenges, limits, passwords, refresh, refreshDays, Clock.systemUTC());
    }

    public TwoStageLoginService(UserRepository users,
            MfaLoginChallengeRepository challenges, LoginAttemptLimitRepository limits,
            PasswordEncoder passwords, JwtRefreshService refresh, int refreshDays, Clock clock) {
        this.users = users; this.challenges = challenges; this.limits = limits;
        this.passwords = passwords;
        this.refresh = refresh; this.refreshDays = refreshDays; this.clock = clock;
        dummyPasswordHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = LoginRejected.class)
    public LoginResult login(String username, String password) {
        if (username == null || username.isBlank() || username.length() > 255
                || password == null || password.isEmpty() || password.length() > 1024) throw rejected();
        User user = users.findForAuthenticationByUsername(username).orElse(null);
        if (user == null) {
            passwords.matches(password, dummyPasswordHash);
            throw rejected();
        }
        Instant now = clock.instant();
        LoginAttemptLimit limit = limit(user, now);
        checkLimit(limit);
        if (!passwords.matches(password, user.getPassword())) fail(limit);
        if (user.getPublicUuid() == null) throw new IllegalStateException("Authenticated account has no public UUID.");
        // Legacy TOTP-only accounts must not silently fall back to password-only
        // authentication after the TOTP API is removed. They remain locked until
        // an administrator completes an explicit WebAuthn migration.
        if (user.isTotpEnabled() && !user.isWebauthnEnabled()) throw rejected();
        if (!user.isWebauthnEnabled()) {
            challenges.consumeOutstanding(user.getId(), now);
            return new LoginResult(refresh.issueSession(user, refreshDays, false), null);
        }
        if (limit.getChallenges() >= MAX_CHALLENGES) throw throttled();
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
        return new LoginResult(null, new MfaRequired(true, raw, challenge.getExpiresAt(), "webauthn"));
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
    public record MfaRequired(boolean mfaRequired, String challengeToken, Instant expiresAt, String method) {
        public MfaRequired(boolean mfaRequired, String challengeToken, Instant expiresAt) {
            this(mfaRequired, challengeToken, expiresAt, "webauthn");
        }
        @Override public String toString() { return "MfaRequired[redacted]"; }
    }
    public record LoginResult(AuthenticatedSession session, MfaRequired challenge) {
        @Override public String toString() { return "LoginResult[redacted]"; }
    }
}
