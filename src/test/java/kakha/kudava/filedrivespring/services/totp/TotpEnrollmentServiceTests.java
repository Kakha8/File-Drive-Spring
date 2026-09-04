package kakha.kudava.filedrivespring.services.totp;

import kakha.kudava.filedrivespring.model.*;
import kakha.kudava.filedrivespring.repository.*;
import kakha.kudava.filedrivespring.services.jwt.JwtRefreshService;
import kakha.kudava.filedrivespring.services.users.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real H2 commits and Spring transaction proxy, including failure and concurrency paths. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop", showSql = false)
@Import(TotpEnrollmentServiceTests.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TotpEnrollmentServiceTests {
    private static final String SEED = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    @Autowired TotpEnrollmentService service;
    @Autowired TotpDeviceRepository devices;
    @Autowired TotpEnrollmentLimitRepository limits;
    @Autowired UserRepository users;
    @Autowired JwtRefresherRepository tokens;
    @Autowired JwtRefreshService refresh;
    @Autowired PasswordEncoder passwords;
    @Autowired Clock clock;
    private User owner;

    @BeforeEach
    void setup() {
        reset(refresh, clock);
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(59));
        tokens.deleteAll();
        devices.deleteAll();
        limits.deleteAll();
        users.deleteAll();
        owner = user("owner");
        authenticate("owner");
    }

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void enrollsAndRevokesRefreshTokensOnlyAfterConfirmation() {
        refresh.createToken(owner, 7);
        User other = user("other");
        refresh.createToken(other, 7);
        var pending = begin();
        TotpDevice saved = device(pending.deviceId());
        assertEquals(TotpDevice.Status.PENDING, saved.getStatus());
        assertEquals(Instant.ofEpochSecond(359), pending.expiresAt());
        assertFalse(users.findById(owner.getId()).orElseThrow().isTotpEnabled());
        assertNull(saved.getLastAcceptedCounter());
        assertEquals(36, saved.getEncryptedSecret().length);
        var confirmed = service.confirm(pending.deviceId(), "287082");
        assertEquals(Instant.ofEpochSecond(59), confirmed.confirmedAt());
        assertTrue(users.findById(owner.getId()).orElseThrow().isTotpEnabled());
        assertEquals(TotpDevice.Status.ACTIVE, device(pending.deviceId()).getStatus());
        assertEquals(1L, device(pending.deviceId()).getLastAcceptedCounter());
        assertTrue(tokens.findAll().stream().filter(t -> t.getUser().getId().equals(owner.getId())).allMatch(JwtRefresher::isRevoked));
        assertTrue(tokens.findAll().stream().filter(t -> t.getUser().getId().equals(other.getId())).noneMatch(JwtRefresher::isRevoked));
        rejected(HttpStatus.CONFLICT, () -> service.confirm(pending.deviceId(), "287082"));
    }

    @Test
    void enforcesPasswordAndPersistsFailureCountersAfterExceptions() {
        for (int i = 0; i < 5; i++) {
            rejected(i == 4 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST,
                    () -> service.begin("device", SEED, "wrong", null, null));
        }
        assertEquals(5, limits.findById(owner.getId()).orElseThrow().getFailures());
        rejected(HttpStatus.TOO_MANY_REQUESTS, this::begin);
        assertEquals(0, devices.count());
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(959));
        assertNotNull(begin());
    }

    @Test
    void wrongConfirmationCodesCommitAttemptsAndExhaustEnrollment() {
        var enrollment = begin();
        for (int i = 0; i < 5; i++) {
            rejected(i == 4 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST,
                    () -> service.confirm(enrollment.deviceId(), "000000"));
        }
        assertEquals(5, device(enrollment.deviceId()).getEnrollmentFailedAttempts());
        assertEquals(TotpDevice.Status.REVOKED, device(enrollment.deviceId()).getStatus());
        assertFalse(users.findById(owner.getId()).orElseThrow().isTotpEnabled());
        rejected(HttpStatus.TOO_MANY_REQUESTS, this::begin);
    }

    @Test
    void restartingInvalidatesPendingButDoesNotResetFailures() {
        var first = begin();
        rejected(HttpStatus.BAD_REQUEST, () -> service.confirm(first.deviceId(), "bad"));
        var second = begin();
        assertEquals(TotpDevice.Status.REVOKED, device(first.deviceId()).getStatus());
        assertEquals(TotpDevice.Status.PENDING, device(second.deviceId()).getStatus());
        assertEquals(1, limits.findById(owner.getId()).orElseThrow().getFailures());
        rejected(HttpStatus.CONFLICT, () -> service.confirm(first.deviceId(), "287082"));
    }

    @Test
    void startLimitAlsoBoundsSuccessfulStarts() {
        for (int i = 0; i < 5; i++) begin();
        rejected(HttpStatus.TOO_MANY_REQUESTS, this::begin);
        assertEquals(1, devices.findAllByUserIdAndStatus(owner.getId(), TotpDevice.Status.PENDING).size());
    }

    @Test
    void expiredAndCrossAccountEnrollmentsCannotActivate() {
        var enrollment = begin();
        user("other");
        authenticate("other");
        rejected(HttpStatus.NOT_FOUND, () -> service.confirm(enrollment.deviceId(), "287082"));
        assertEquals(TotpDevice.Status.PENDING, device(enrollment.deviceId()).getStatus());
        authenticate("owner");
        when(clock.instant()).thenReturn(enrollment.expiresAt());
        rejected(HttpStatus.GONE, () -> service.confirm(enrollment.deviceId(), "287082"));
        assertEquals(TotpDevice.Status.REVOKED, device(enrollment.deviceId()).getStatus());
    }

    @Test
    void anonymousOrUnauthenticatedRequestsAreRejected() {
        SecurityContextHolder.clearContext();
        rejected(HttpStatus.UNAUTHORIZED, this::begin);
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "owner", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        rejected(HttpStatus.UNAUTHORIZED, this::begin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("owner", "password"));
        rejected(HttpStatus.UNAUTHORIZED, this::begin);
        assertEquals(0, limits.count());
    }

    @Test
    void invalidInputsDoNotCreateDevicesOrExposeSecrets() {
        rejected(HttpStatus.BAD_REQUEST, () -> service.begin(" ", SEED, "password", null, null));
        var failure = assertThrows(TotpEnrollmentService.EnrollmentRejected.class,
                () -> service.begin("name", "secret-invalid", "password", null, null));
        assertFalse(failure.getMessage().contains("secret-invalid"));
        assertEquals(0, devices.count());
    }

    @Test
    void additionalDeviceRequiresExistingFactorAndConsumesItsCounter() {
        var first = begin();
        service.confirm(first.deviceId(), "287082");
        rejected(HttpStatus.BAD_REQUEST, this::begin);
        rejected(HttpStatus.BAD_REQUEST,
                () -> service.begin("second", SEED, "password", first.deviceId(), "287082"));
        // RFC HOTP counter 2 for this seed; first factor's enrollment consumed counter 1.
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(60));
        var second = service.begin("second", SEED, "password", first.deviceId(), "359152");
        assertEquals(2L, device(first.deviceId()).getLastAcceptedCounter());
        service.confirm(second.deviceId(), "359152");
        assertEquals(2, devices.findAllByUserIdAndStatus(owner.getId(), TotpDevice.Status.ACTIVE).size());
    }

    @Test
    void removingLastDeviceDisablesTotpAndRevokesSessions() {
        var enrollment = begin();
        service.confirm(enrollment.deviceId(), "287082");
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(60));

        var result = service.remove(enrollment.deviceId(), "password", enrollment.deviceId(), "359152");

        assertFalse(result.enabled());
        assertEquals(0, result.remainingDevices());
        assertFalse(users.findById(owner.getId()).orElseThrow().isTotpEnabled());
        assertEquals(TotpDevice.Status.REVOKED, device(enrollment.deviceId()).getStatus());
        verify(refresh, times(2)).revokeAllForUser(owner.getId());
    }

    @Test
    void multipleDevicesRequireAnotherDeviceToAuthorizeRemoval() {
        var first = begin();
        service.confirm(first.deviceId(), "287082");
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(60));
        var second = service.begin("second", SEED, "password", first.deviceId(), "359152");
        service.confirm(second.deviceId(), "359152");
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(90));

        rejected(HttpStatus.BAD_REQUEST,
                () -> service.remove(first.deviceId(), "password", first.deviceId(), "969429"));
        var result = service.remove(first.deviceId(), "password", second.deviceId(), "969429");

        assertTrue(result.enabled());
        assertEquals(1, result.remainingDevices());
        assertEquals(TotpDevice.Status.REVOKED, device(first.deviceId()).getStatus());
        assertEquals(TotpDevice.Status.ACTIVE, device(second.deviceId()).getStatus());
    }

    @Test
    void revokingAuthorizingDeviceInvalidatesPendingAuthorization() {
        var first = begin();
        service.confirm(first.deviceId(), "287082");
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(60));
        var second = service.begin("second", SEED, "password", first.deviceId(), "359152");
        TotpDevice old = device(first.deviceId());
        old.setStatus(TotpDevice.Status.REVOKED);
        devices.saveAndFlush(old);
        rejected(HttpStatus.FORBIDDEN, () -> service.confirm(second.deviceId(), "359152"));
        assertEquals(TotpDevice.Status.PENDING, device(second.deviceId()).getStatus());
    }

    @Test
    void infrastructureFailureRollsBackActivationAndRefreshRevocation() {
        refresh.createToken(owner, 7);
        var enrollment = begin();
        doAnswer(call -> {
            call.callRealMethod();
            throw new IllegalStateException("simulated failure after revocation");
        }).when(refresh).revokeAllForUser(owner.getId());
        assertThrows(IllegalStateException.class, () -> service.confirm(enrollment.deviceId(), "287082"));
        assertFalse(users.findById(owner.getId()).orElseThrow().isTotpEnabled());
        assertEquals(TotpDevice.Status.PENDING, device(enrollment.deviceId()).getStatus());
        assertNull(device(enrollment.deviceId()).getLastAcceptedCounter());
        assertTrue(tokens.findAll().stream().noneMatch(JwtRefresher::isRevoked));
    }

    @Test
    void simultaneousConfirmationOnlySucceedsOnce() throws Exception {
        var enrollment = begin();
        Callable<Boolean> attempt = () -> {
            authenticate("owner");
            try {
                service.confirm(enrollment.deviceId(), "287082");
                return true;
            } catch (TotpEnrollmentService.EnrollmentRejected rejection) {
                assertEquals(HttpStatus.CONFLICT, rejection.getStatusCode());
                return false;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(attempt, attempt), 15, TimeUnit.SECONDS);
            assertNotEquals(results.get(0).get(), results.get(1).get());
        }
        verify(refresh, times(1)).revokeAllForUser(owner.getId());
    }

    private TotpEnrollmentService.Enrollment begin() {
        return service.begin("My ESP32", SEED, "password", null, null);
    }

    private TotpDevice device(Long id) { return devices.findById(id).orElseThrow(); }

    private User user(String name) {
        User user = new User();
        user.setUsername(name);
        user.setPassword(passwords.encode("password"));
        user.setRole(User.Role.USER);
        return users.saveAndFlush(user);
    }

    private static void authenticate(String name) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                name, null, AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    private static void rejected(HttpStatus status, Runnable action) {
        assertEquals(status, assertThrows(TotpEnrollmentService.EnrollmentRejected.class, action::run).getStatusCode());
    }

    @TestConfiguration
    static class Config {
        @Bean Clock clock() { return mock(Clock.class); }
        @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(4); }
        @Bean TotpVerificationService verifier(Clock clock) { return new TotpVerificationService(clock); }
        @Bean TotpSecretEncryptionService encryption() {
            return new TotpSecretEncryptionService("test-v1", "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        }
        @Bean JwtRefreshService refresh(JwtRefresherRepository tokens, UserRepository users) {
            return spy(new JwtRefreshService(users, mock(kakha.kudava.filedrivespring.services.jwt.JwtService.class), tokens));
        }
        @Bean TotpEnrollmentService enrollment(UserRepository users, TotpDeviceRepository devices,
                TotpEnrollmentLimitRepository limits, PasswordEncoder passwords,
                TotpSecretEncryptionService encryption, TotpVerificationService verifier,
                JwtRefreshService refresh, Clock clock) {
            return new TotpEnrollmentService(users, devices, limits, passwords, encryption, verifier, refresh, clock);
        }
    }
}
