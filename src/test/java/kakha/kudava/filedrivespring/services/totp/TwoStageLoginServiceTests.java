package kakha.kudava.filedrivespring.services.totp;

import kakha.kudava.filedrivespring.model.*;
import kakha.kudava.filedrivespring.repository.*;
import kakha.kudava.filedrivespring.services.jwt.*;
import kakha.kudava.filedrivespring.security.TokenHashUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop", showSql = false)
@Import(TwoStageLoginServiceTests.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TwoStageLoginServiceTests {
    @Autowired TwoStageLoginService login;
    @Autowired JwtRefreshService refresh;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired TotpDeviceRepository devices;
    @Autowired MfaLoginChallengeRepository challenges;
    @Autowired LoginAttemptLimitRepository limits;
    @Autowired JwtRefresherRepository tokens;
    @Autowired PasswordEncoder passwords;
    @Autowired TotpSecretEncryptionService encryption;
    @Autowired TotpVerificationService verifier;
    @Autowired Clock clock;
    @Autowired PlatformTransactionManager transactions;
    User owner;

    @BeforeEach
    void setup() {
        reset(clock); clearInvocations(jwt);
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(59));
        tokens.deleteAll(); challenges.deleteAll(); devices.deleteAll(); limits.deleteAll(); users.deleteAll();
        owner = new User(); owner.setUsername("alice"); owner.setPassword(passwords.encode("password")); owner.setRole(User.Role.USER);
        owner = users.saveAndFlush(owner);
    }

    @Test
    void passwordOnlyLoginPreservesAccountResponseAndRefreshWorks() {
        var result = login.login("alice", "password");
        assertNull(result.challenge()); assertNotNull(result.session());
        assertEquals(owner.getPublicUuid(), result.session().login().getPublicUuid());
        assertEquals("alice", jwt.parse(result.session().login().getAccessToken()).getPayload().getSubject());
        assertEquals(0, challenges.count());
        assertFalse(tokens.findAll().getFirst().isMfaVerified());
        var rotated = refresh.rotate(result.session().refreshToken(), 7);
        assertNotEquals(result.session().refreshToken(), rotated.refreshToken());
        assertEquals(owner.getPublicUuid(), rotated.login().getPublicUuid());
        assertThrows(JwtRefreshService.RefreshRejected.class, () -> refresh.rotate(result.session().refreshToken(), 7));
    }

    @Test
    void passwordSuccessOnlyCreatesHashedChallengeThenMfaCreatesSession() {
        TotpDevice device = enable();
        clearInvocations(jwt);
        var result = login.login("alice", "password");
        assertNull(result.session()); assertTrue(result.challenge().mfaRequired());
        assertEquals(Instant.ofEpochSecond(239), result.challenge().expiresAt());
        assertEquals(0, tokens.count()); verify(jwt, never()).generateAccessToken(any());
        String raw = result.challenge().challengeToken();
        assertEquals(43, raw.length()); assertFalse(challenges.existsById(raw));
        assertTrue(challenges.existsById(TokenHashUtil.sha256(raw)));
        var session = login.verify(raw, "287082");
        assertNotNull(session.login().getAccessToken()); assertEquals(1, tokens.count());
        assertTrue(tokens.findAll().getFirst().isMfaVerified());
        assertEquals(1L, devices.findById(device.getId()).orElseThrow().getLastAcceptedCounter());
        assertNotNull(challenges.findById(TokenHashUtil.sha256(raw)).orElseThrow().getConsumedAt());
        assertNotNull(refresh.rotate(session.refreshToken(), 7));
    }

    @Test
    void rejectsWrongPasswordsAndSharesFailureBudgetWithMfa() {
        enable();
        var challenge = login.login("alice", "password").challenge();
        for (int i = 0; i < 5; i++) rejected(HttpStatus.UNAUTHORIZED, () -> login.login("alice", "wrong"));
        for (int i = 0; i < 5; i++) {
            HttpStatus expected = i == 4 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.UNAUTHORIZED;
            rejected(expected, () -> login.verify(challenge.challengeToken(), "000000"));
        }
        assertEquals(10, limits.findById(owner.getId()).orElseThrow().getFailures());
        rejected(HttpStatus.TOO_MANY_REQUESTS, () -> login.login("alice", "password"));
        assertEquals(0, tokens.count());
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(959));
        assertNotNull(login.login("alice", "password").challenge());
    }

    @Test
    void challengeReplacementDoesNotResetFailuresAndOldChallengeStopsWorking() {
        enable();
        String first = challenge();
        rejected(HttpStatus.UNAUTHORIZED, () -> login.verify(first, "000000"));
        String second = challenge();
        assertNotEquals(first, second);
        rejected(HttpStatus.UNAUTHORIZED, () -> login.verify(first, "287082"));
        assertEquals(1, limits.findById(owner.getId()).orElseThrow().getFailures());
        assertNotNull(login.verify(second, "287082"));
    }

    @Test
    void challengeIssuanceIsAlsoLimited() {
        enable();
        for (int i = 0; i < 10; i++) challenge();
        rejected(HttpStatus.TOO_MANY_REQUESTS, this::challenge);
        assertEquals(0, tokens.count());
    }

    @Test
    void expiryPasswordChangesAndDisabledMfaInvalidateChallenges() {
        enable();
        String expired = challenge();
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(239));
        rejected(HttpStatus.UNAUTHORIZED, () -> login.verify(expired, "287082"));
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(59));
        String passwordChanged = challenge();
        owner.setPassword(passwords.encode("new-password")); users.saveAndFlush(owner);
        rejected(HttpStatus.UNAUTHORIZED, () -> login.verify(passwordChanged, "287082"));
        String disabled = login.login("alice", "new-password").challenge().challengeToken();
        owner.setTotpEnabled(false); users.saveAndFlush(owner);
        rejected(HttpStatus.UNAUTHORIZED, () -> login.verify(disabled, "287082"));
        assertEquals(0, tokens.count());
    }

    @Test
    void cannotReplayCodeAcrossFreshChallengesOrReuseConsumedChallenge() {
        enable();
        String first = challenge(); login.verify(first, "287082");
        rejected(HttpStatus.UNAUTHORIZED, () -> login.verify(first, "287082"));
        String second = challenge();
        rejected(HttpStatus.UNAUTHORIZED, () -> login.verify(second, "287082"));
        assertEquals(1, tokens.count());
    }

    @Test
    void noActiveDeviceFailsClosedAndRevokedDeviceCannotVerify() {
        owner.setTotpEnabled(true); users.saveAndFlush(owner);
        rejected(HttpStatus.UNAUTHORIZED, this::challenge);
        TotpDevice device = enable();
        String raw = challenge();
        device.setStatus(TotpDevice.Status.REVOKED); devices.saveAndFlush(device);
        rejected(HttpStatus.UNAUTHORIZED, () -> login.verify(raw, "287082"));
        assertEquals(0, tokens.count());
    }

    @Test
    void malformedUnknownAndCrossAccountCredentialsFail() {
        rejected(HttpStatus.UNAUTHORIZED, () -> login.login("unknown", "password"));
        rejected(HttpStatus.UNAUTHORIZED, () -> login.verify("invalid", "287082"));
        rejected(HttpStatus.UNAUTHORIZED, () -> login.verify("A".repeat(43), "287082"));
        enable(); String raw = challenge();
        rejected(HttpStatus.UNAUTHORIZED, () -> login.verify(raw, null));
        rejected(HttpStatus.UNAUTHORIZED, () -> login.verify(raw, "２８７０８２"));
        assertEquals(0, tokens.count());
    }

    @Test
    void oldPasswordOnlyRefreshCannotBypassEnabledMfa() {
        var old = login.login("alice", "password").session();
        enable();
        assertThrows(JwtRefreshService.RefreshRejected.class, () -> refresh.rotate(old.refreshToken(), 7));
        assertEquals(1, tokens.count()); assertTrue(tokens.findAll().getFirst().isRevoked());
    }

    @Test
    void jwtIssuanceFailureRollsBackChallengeAndCounter() {
        TotpDevice device = enable(); String raw = challenge();
        doThrow(new IllegalStateException("simulated issuance failure")).when(jwt).generateAccessToken(any());
        try {
            assertThrows(IllegalStateException.class, () -> login.verify(raw, "287082"));
            assertEquals(0, tokens.count());
            assertNull(challenges.findById(TokenHashUtil.sha256(raw)).orElseThrow().getConsumedAt());
            assertNull(devices.findById(device.getId()).orElseThrow().getLastAcceptedCounter());
        } finally { doCallRealMethod().when(jwt).generateAccessToken(any()); }
    }

    @Test
    void concurrentMfaAndRefreshEachSucceedOnlyOnce() throws Exception {
        enable(); String raw = challenge();
        Callable<AuthenticatedSession> verify = () -> {
            try { return login.verify(raw, "287082"); }
            catch (TwoStageLoginService.LoginRejected rejected) { return null; }
        };
        AuthenticatedSession session = race(verify);
        assertEquals(1, tokens.count());
        Callable<AuthenticatedSession> rotate = () -> {
            try { return refresh.rotate(session.refreshToken(), 7); }
            catch (JwtRefreshService.RefreshRejected rejected) { return null; }
        };
        assertNotNull(race(rotate));
        assertEquals(1, tokens.findAll().stream().filter(t -> !t.isRevoked()).count());
    }

    @Test
    void accessJwtSurvivesMfaActivationAndRefreshRevocationWithOriginalExpiry() throws Exception {
        var session = login.login("alice", "password").session();
        String access = session.login().getAccessToken();
        var expiration = jwt.parse(access).getPayload().getExpiration();
        enable();
        refresh.revokeAllForUser(owner.getId());
        assertThrows(JwtRefreshService.RefreshRejected.class, () -> refresh.rotate(session.refreshToken(), 7));
        assertEquals(expiration, jwt.parse(access).getPayload().getExpiration());

        // Verify acceptance through the real filter, not just JWT signature parsing.
        var filter = new JwtFilter(jwt, username -> org.springframework.security.core.userdetails.User
                .withUsername(username).password("unused").roles("USER").build());
        var request = new MockHttpServletRequest("GET", "/api/files");
        request.setServletPath("/api/files");
        request.addHeader("Authorization", "Bearer " + access);
        try {
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                assertNotNull(authentication);
                assertTrue(authentication.isAuthenticated());
                assertEquals("alice", authentication.getName());
            });
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void revocationFirstPreventsConcurrentRefreshFromCreatingReplacement() throws Exception {
        var session = login.login("alice", "password").session();
        CountDownLatch revoked = new CountDownLatch(1), releaseCommit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var revocation = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                refresh.revokeAllForUser(owner.getId());
                revoked.countDown();
                await(releaseCommit);
            }));
            try {
                assertTrue(revoked.await(10, TimeUnit.SECONDS));
                var rotation = executor.submit(() -> {
                    assertThrows(JwtRefreshService.RefreshRejected.class, () -> refresh.rotate(session.refreshToken(), 7));
                });
                releaseCommit.countDown();
                revocation.get(10, TimeUnit.SECONDS);
                rotation.get(10, TimeUnit.SECONDS);
            } finally { releaseCommit.countDown(); }
        }
        assertEquals(1, tokens.count());
        assertTrue(tokens.findAll().stream().allMatch(JwtRefresher::isRevoked));
    }

    @Test
    void refreshFirstStillLosesItsReplacementToConcurrentRevocation() throws Exception {
        var session = login.login("alice", "password").session();
        CountDownLatch rotated = new CountDownLatch(1), releaseCommit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var rotation = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                var replacement = refresh.rotate(session.refreshToken(), 7);
                rotated.countDown();
                await(releaseCommit);
                return replacement;
            }));
            try {
                assertTrue(rotated.await(10, TimeUnit.SECONDS));
                var revocation = executor.submit(() -> refresh.revokeAllForUser(owner.getId()));
                releaseCommit.countDown();
                var replacement = rotation.get(10, TimeUnit.SECONDS);
                revocation.get(10, TimeUnit.SECONDS);
                assertThrows(JwtRefreshService.RefreshRejected.class, () -> refresh.rotate(replacement.refreshToken(), 7));
                assertNotNull(jwt.parse(replacement.login().getAccessToken()));
            } finally { releaseCommit.countDown(); }
        }
        assertEquals(2, tokens.count());
        assertTrue(tokens.findAll().stream().allMatch(JwtRefresher::isRevoked));
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }

    private AuthenticatedSession race(Callable<AuthenticatedSession> action) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(action, action), 15, TimeUnit.SECONDS);
            var first = results.get(0).get(); var second = results.get(1).get();
            assertTrue((first == null) != (second == null));
            return first == null ? second : first;
        }
    }

    private String challenge() { return login.login("alice", "password").challenge().challengeToken(); }
    private TotpDevice enable() {
        owner.setTotpEnabled(true); owner = users.saveAndFlush(owner);
        byte[] seed = verifier.decodeBase32Secret("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        var encrypted = encryption.encrypt(owner.getPublicUuid(), seed);
        TotpDevice device = new TotpDevice(); device.setUser(owner); device.setDisplayName("ESP32");
        device.setStatus(TotpDevice.Status.ACTIVE); device.setCreatedAt(clock.instant());
        device.setEncryptedSecret(encrypted.ciphertext()); device.setEncryptionNonce(encrypted.nonce()); device.setEncryptionKeyId(encrypted.keyId());
        return devices.saveAndFlush(device);
    }
    private static void rejected(HttpStatus status, Runnable action) {
        assertEquals(status, assertThrows(TwoStageLoginService.LoginRejected.class, action::run).getStatusCode());
    }

    @TestConfiguration
    static class Config {
        @Bean Clock clock() { return mock(Clock.class); }
        @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(4); }
        @Bean JwtService jwt() { return spy(new JwtService("test-only-signing-key-at-least-32-bytes-long", 15)); }
        @Bean TotpVerificationService verifier(Clock clock) { return new TotpVerificationService(clock); }
        @Bean TotpSecretEncryptionService encryption() { return new TotpSecretEncryptionService("test", "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="); }
        @Bean JwtRefreshService refresh(UserRepository users, JwtService jwt, JwtRefresherRepository tokens) {
            return new JwtRefreshService(users, jwt, tokens);
        }
        @Bean TwoStageLoginService login(UserRepository users, TotpDeviceRepository devices,
                MfaLoginChallengeRepository challenges, LoginAttemptLimitRepository limits, PasswordEncoder passwords,
                TotpSecretEncryptionService encryption, TotpVerificationService verifier, JwtRefreshService refresh, Clock clock) {
            return new TwoStageLoginService(users, devices, challenges, limits, passwords, encryption, verifier, refresh, 7, clock);
        }
    }
}
