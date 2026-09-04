package kakha.kudava.filedrivespring.services.jwt;

import kakha.kudava.filedrivespring.model.JwtRefresher;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.JwtRefresherRepository;
import kakha.kudava.filedrivespring.security.TokenHashUtil;
import kakha.kudava.filedrivespring.repository.UserRepository;
import kakha.kudava.filedrivespring.dto.LoginResponse;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class JwtRefreshService {

    private final UserRepository users;
    private final JwtService jwt;
    private final JwtRefresherRepository repo;
    private static final SecureRandom random = new SecureRandom();

    public JwtRefreshService(UserRepository users, JwtService jwt, JwtRefresherRepository jwtRefresherRepository) {
        this.users = users;
        this.jwt = jwt;
        this.repo = jwtRefresherRepository;
    }

    @Transactional
    public String createToken(User user, int daysValid) {
        User locked = users.findForAuthenticationUpdate(user.getId())
                .orElseThrow(() -> new IllegalStateException("Account not found."));
        return createToken(locked, daysValid, false);
    }

    private String createToken(User user, int daysValid, boolean mfaVerified) {
        if (user.isTotpEnabled() && !mfaVerified) throw new IllegalStateException("MFA verification required.");
        String refreshToken = generateRandomToken();
        String hash = TokenHashUtil.sha256(refreshToken);

        JwtRefresher entity = new JwtRefresher();
        entity.setUser(user);
        entity.setTokenHash(hash);
        entity.setExpiresAt(LocalDateTime.now().plusDays(daysValid));
        entity.setRevoked(false);
        entity.setMfaVerified(mfaVerified);

        repo.save(entity);
        return refreshToken;
    }

    /** Caller must hold the User lock and have completed the required factors. */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuthenticatedSession issueSession(User user, int daysValid, boolean mfaVerified) {
        if (user.getPublicUuid() == null) throw new IllegalStateException("Authenticated account has no public UUID.");
        if (user.isTotpEnabled() && !mfaVerified) throw new IllegalStateException("MFA verification required.");
        var details = org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                .password(user.getPassword()).roles(user.getRole().name()).build();
        String access = jwt.generateAccessToken(details);
        String refresh = createToken(user, daysValid, mfaVerified);
        return new AuthenticatedSession(new LoginResponse(access, user.getId(), user.getUsername(), user.getPublicUuid()), refresh);
    }

    /** User -> refresh lock order coordinates rotation with TOTP activation/revocation. */
    @Transactional(noRollbackFor = RefreshRejected.class)
    public AuthenticatedSession rotate(String rawToken, int daysValid) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{86}")) throw new RefreshRejected();
        String hash = TokenHashUtil.sha256(rawToken);
        Long ownerId = repo.findOwnerId(hash).orElseThrow(RefreshRejected::new);
        User user = users.findForAuthenticationUpdate(ownerId).orElseThrow(RefreshRejected::new);
        JwtRefresher stored = repo.findForRotation(hash, ownerId).orElseThrow(RefreshRejected::new);
        if (stored.isRevoked()) throw new RefreshRejected();
        if (!stored.getExpiresAt().isAfter(LocalDateTime.now()) || (user.isTotpEnabled() && !stored.isMfaVerified())) {
            stored.setRevoked(true);
            throw new RefreshRejected();
        }
        stored.setRevoked(true);
        stored.setLastUsedAt(LocalDateTime.now());
        return issueSession(user, daysValid, stored.isMfaVerified());
    }

    public static final class RefreshRejected extends ResponseStatusException {
        public RefreshRejected() { super(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh session. Log in again."); }
    }

    public JwtRefresher validateToken(String token) {
        String hashedToken = TokenHashUtil.sha256(token);
        JwtRefresher stored = repo.findByTokenHashAndRevokedFalse(hashedToken)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            stored.setRevoked(true);
            repo.save(stored);
            throw new RuntimeException("Refresh token expired");
        }
        stored.setLastUsedAt(LocalDateTime.now());
        repo.save(stored);

        return stored;
    }

    public void revoke(JwtRefresher refresher) {
        refresher.setRevoked(true);
        repo.save(refresher);
    }

    @Transactional
    public int revokeAllForUser(Long userId) {
        users.findForAuthenticationUpdate(userId).orElseThrow(() -> new IllegalStateException("Account not found."));
        return repo.revokeAllActiveByUserId(userId);
    }
    private String generateRandomToken() {
        byte[] bytes = new byte[64];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
