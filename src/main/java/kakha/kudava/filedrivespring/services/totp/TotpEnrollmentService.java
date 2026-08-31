package kakha.kudava.filedrivespring.services.totp;

import kakha.kudava.filedrivespring.model.TotpDevice;
import kakha.kudava.filedrivespring.model.TotpEnrollmentLimit;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.TotpDeviceRepository;
import kakha.kudava.filedrivespring.repository.TotpEnrollmentLimitRepository;
import kakha.kudava.filedrivespring.repository.UserRepository;
import kakha.kudava.filedrivespring.services.jwt.JwtRefreshService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.OptionalLong;

/** Enrollment activation is coordinated with two-stage login through the account lock. */
@Service
public class TotpEnrollmentService {
    private static final Duration ENROLLMENT_TTL = Duration.ofMinutes(5);
    private static final Duration LIMIT_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_FAILURES = 5;
    private static final int MAX_STARTS = 5;
    private final UserRepository users;
    private final TotpDeviceRepository devices;
    private final TotpEnrollmentLimitRepository limits;
    private final PasswordEncoder passwords;
    private final TotpSecretEncryptionService encryption;
    private final TotpVerificationService verifier;
    private final JwtRefreshService refresh;
    private final Clock clock;

    @Autowired
    public TotpEnrollmentService(UserRepository users, TotpDeviceRepository devices,
            TotpEnrollmentLimitRepository limits, PasswordEncoder passwords,
            TotpSecretEncryptionService encryption, TotpVerificationService verifier, JwtRefreshService refresh) {
        this(users, devices, limits, passwords, encryption, verifier, refresh, Clock.systemUTC());
    }

    public TotpEnrollmentService(UserRepository users, TotpDeviceRepository devices,
            TotpEnrollmentLimitRepository limits, PasswordEncoder passwords,
            TotpSecretEncryptionService encryption, TotpVerificationService verifier,
            JwtRefreshService refresh, Clock clock) {
        this.users = users;
        this.devices = devices;
        this.limits = limits;
        this.passwords = passwords;
        this.encryption = encryption;
        this.verifier = verifier;
        this.refresh = refresh;
        this.clock = clock;
    }

    /**
     * The authenticated caller supplies no owner ID. Existing factor arguments are
     * required when MFA is already enabled. Recovery-code step-up is not implemented.
     * Each call owns its transaction so rejected attempts commit their limit counters.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = EnrollmentRejected.class)
    public Enrollment begin(String displayName, String secretBase32, String password,
                            Long existingDeviceId, String existingCode) {
        User user = currentUserLocked();
        Instant now = clock.instant();
        TotpEnrollmentLimit limit = limit(user, now);
        checkFailureLimit(limit);
        if (limit.getStarts() >= MAX_STARTS) {
            throw rejected(HttpStatus.TOO_MANY_REQUESTS, "Too many enrollment starts. Try again later.");
        }
        // Includes invalid/password-rejected starts, not just successful enrollment records.
        limit.setStarts(limit.getStarts() + 1);
        if (password == null || password.length() > 1024 || !passwords.matches(password, user.getPassword())) {
            fail(limit, "Password verification failed.");
        }
        if (displayName == null || displayName.isBlank() || displayName.length() > 100) {
            throw rejected(HttpStatus.BAD_REQUEST, "Device name must contain 1 to 100 characters.");
        }
        byte[] seed;
        try {
            seed = verifier.decodeBase32Secret(secretBase32);
        } catch (IllegalArgumentException exception) {
            throw rejected(HttpStatus.BAD_REQUEST, "Invalid device seed format.");
        }
        try {
            TotpDevice authorizer = null;
            if (user.isTotpEnabled()) {
                authorizer = existingDeviceId == null ? null
                        : devices.findForEnrollmentUpdate(existingDeviceId, user.getId()).orElse(null);
                if (authorizer == null || authorizer.getStatus() != TotpDevice.Status.ACTIVE) {
                    fail(limit, "Existing authenticator verification failed.");
                }
                OptionalLong matched = verifyStored(user, authorizer, existingCode);
                if (matched.isEmpty()) fail(limit, "Existing authenticator verification failed.");
                authorizer.setLastAcceptedCounter(matched.orElseThrow());
            }
            var encrypted = encryption.encrypt(user.getPublicUuid(), seed);
            // Only one current pending enrollment per account. Starting again never
            // removes ACTIVE factors or clears the account-level attempt counters.
            for (TotpDevice pending : devices.findAllByUserIdAndStatus(user.getId(), TotpDevice.Status.PENDING)) {
                pending.setStatus(TotpDevice.Status.REVOKED);
            }
            TotpDevice device = new TotpDevice();
            device.setUser(user);
            device.setDisplayName(displayName.strip());
            device.setEncryptedSecret(encrypted.ciphertext());
            device.setEncryptionNonce(encrypted.nonce());
            device.setEncryptionKeyId(encrypted.keyId());
            device.setStatus(TotpDevice.Status.PENDING);
            device.setCreatedAt(now);
            device.setEnrollmentExpiresAt(now.plus(ENROLLMENT_TTL));
            device.setEnrollmentAuthorizingDeviceId(authorizer == null ? null : authorizer.getId());
            devices.saveAndFlush(device);
            return new Enrollment(device.getId(), device.getDisplayName(), device.getEnrollmentExpiresAt());
        } finally {
            Arrays.fill(seed, (byte) 0);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = EnrollmentRejected.class)
    public Confirmation confirm(Long deviceId, String code) {
        User user = currentUserLocked();
        Instant now = clock.instant();
        TotpEnrollmentLimit limit = limit(user, now);
        checkFailureLimit(limit);
        TotpDevice device = deviceId == null ? null
                : devices.findForEnrollmentUpdate(deviceId, user.getId()).orElse(null);
        if (device == null) throw rejected(HttpStatus.NOT_FOUND, "Enrollment not found.");
        if (device.getStatus() != TotpDevice.Status.PENDING) {
            throw rejected(HttpStatus.CONFLICT, "Enrollment is not pending.");
        }
        if (device.getEnrollmentExpiresAt() == null || !now.isBefore(device.getEnrollmentExpiresAt())) {
            device.setStatus(TotpDevice.Status.REVOKED);
            throw rejected(HttpStatus.GONE, "Enrollment has expired. Start again.");
        }
        if (device.getEnrollmentFailedAttempts() >= MAX_FAILURES) {
            device.setStatus(TotpDevice.Status.REVOKED);
            throw rejected(HttpStatus.TOO_MANY_REQUESTS, "Enrollment attempt limit reached.");
        }
        if (user.isTotpEnabled()) {
            Long authorizerId = device.getEnrollmentAuthorizingDeviceId();
            TotpDevice authorizer = authorizerId == null ? null
                    : devices.findForEnrollmentUpdate(authorizerId, user.getId()).orElse(null);
            if (authorizer == null || authorizer.getStatus() != TotpDevice.Status.ACTIVE) {
                throw rejected(HttpStatus.FORBIDDEN, "Existing authenticator authorization is no longer valid.");
            }
        }
        OptionalLong matched = verifyStored(user, device, code);
        if (matched.isEmpty()) {
            device.setEnrollmentFailedAttempts(device.getEnrollmentFailedAttempts() + 1);
            if (device.getEnrollmentFailedAttempts() >= MAX_FAILURES) device.setStatus(TotpDevice.Status.REVOKED);
            fail(limit, "Invalid authenticator code.");
        }
        device.setStatus(TotpDevice.Status.ACTIVE);
        device.setConfirmedAt(now);
        device.setLastAcceptedCounter(matched.orElseThrow());
        user.setTotpEnabled(true);
        refresh.revokeAllForUser(user.getId());
        // Flush here; a database/refresh failure rolls the entire activation back.
        devices.flush();
        return new Confirmation(device.getId(), device.getDisplayName(), now);
    }

    private OptionalLong verifyStored(User user, TotpDevice device, String code) {
        byte[] seed = encryption.decrypt(user.getPublicUuid(), device.getEncryptedSecret(),
                device.getEncryptionNonce(), device.getEncryptionKeyId());
        try {
            return verifier.verify(seed, code, device.getLastAcceptedCounter());
        } finally {
            Arrays.fill(seed, (byte) 0);
        }
    }

    private User currentUserLocked() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw rejected(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        // Lock order is always User -> device. Future MFA/reset writers must follow it.
        return users.findForTotpEnrollment(auth.getName())
                .orElseThrow(() -> rejected(HttpStatus.UNAUTHORIZED, "Authentication required."));
    }

    private TotpEnrollmentLimit limit(User user, Instant now) {
        TotpEnrollmentLimit limit = limits.findById(user.getId()).orElseGet(() -> {
            TotpEnrollmentLimit created = new TotpEnrollmentLimit();
            created.setUser(user);
            created.setWindowStartedAt(now);
            return limits.saveAndFlush(created);
        });
        if (!now.isBefore(limit.getWindowStartedAt().plus(LIMIT_WINDOW))) {
            limit.setWindowStartedAt(now);
            limit.setStarts(0);
            limit.setFailures(0);
        }
        return limit;
    }

    private void checkFailureLimit(TotpEnrollmentLimit limit) {
        if (limit.getFailures() >= MAX_FAILURES) {
            throw rejected(HttpStatus.TOO_MANY_REQUESTS, "Too many enrollment attempts. Try again later.");
        }
    }

    private void fail(TotpEnrollmentLimit limit, String message) {
        limit.setFailures(limit.getFailures() + 1);
        throw rejected(limit.getFailures() >= MAX_FAILURES ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST, message);
    }

    private static EnrollmentRejected rejected(HttpStatus status, String message) {
        return new EnrollmentRejected(status, message);
    }

    /** Deliberately commits only anticipated rejections, preserving abuse counters. */
    public static final class EnrollmentRejected extends ResponseStatusException {
        private EnrollmentRejected(HttpStatus status, String message) { super(status, message); }
    }

    // Safe response projections: never return the JPA entity containing the encrypted seed.
    public record Enrollment(Long deviceId, String displayName, Instant expiresAt) {}
    public record Confirmation(Long deviceId, String displayName, Instant confirmedAt) {}
}
