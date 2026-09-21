package kakha.kudava.filedrivespring.services.totp;

import kakha.kudava.filedrivespring.model.LoginAttemptLimit;
import kakha.kudava.filedrivespring.model.MfaLoginChallenge;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.LoginAttemptLimitRepository;
import kakha.kudava.filedrivespring.repository.MfaLoginChallengeRepository;
import kakha.kudava.filedrivespring.repository.TotpDeviceRepository;
import kakha.kudava.filedrivespring.repository.UserRepository;
import kakha.kudava.filedrivespring.services.jwt.AuthenticatedSession;
import kakha.kudava.filedrivespring.services.jwt.JwtRefreshService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FidoLoginServiceTests {
    private final UserRepository users = mock(UserRepository.class);
    private final MfaLoginChallengeRepository challenges = mock(MfaLoginChallengeRepository.class);
    private final LoginAttemptLimitRepository limits = mock(LoginAttemptLimitRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final JwtRefreshService refresh = mock(JwtRefreshService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC);
    private TwoStageLoginService service;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        user.setPublicUuid(UUID.randomUUID());
        user.setUsername("alice");
        user.setPassword("encoded");
        user.setRole(User.Role.USER);
        when(users.findForAuthenticationByUsername("alice")).thenReturn(Optional.of(user));
        when(passwords.matches("password", "encoded")).thenReturn(true);
        when(passwords.encode(anyString())).thenReturn("dummy");
        var limit = new LoginAttemptLimit();
        limit.setUser(user);
        limit.setWindowStartedAt(clock.instant());
        when(limits.findById(7L)).thenReturn(Optional.of(limit));
        when(challenges.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        service = new TwoStageLoginService(users, challenges, limits, passwords,
                refresh, 7, clock);
    }

    @Test
    void passwordOnlyAccountReceivesSession() {
        var session = mock(AuthenticatedSession.class);
        when(refresh.issueSession(user, 7, false)).thenReturn(session);

        var result = service.login("alice", "password");

        assertSame(session, result.session());
        assertNull(result.challenge());
        verify(challenges).consumeOutstanding(7L, clock.instant());
    }

    @Test
    void webAuthnAccountReceivesOnlyWebAuthnChallenge() {
        user.setWebauthnEnabled(true);

        var result = service.login("alice", "password");

        assertNull(result.session());
        assertNotNull(result.challenge());
        assertEquals("webauthn", result.challenge().method());
        assertTrue(result.challenge().mfaRequired());
    }

    @Test
    void legacyTotpOnlyAccountFailsClosedInsteadOfDowngradingToPasswordOnly() {
        user.setTotpEnabled(true);

        assertThrows(TwoStageLoginService.LoginRejected.class,
                () -> service.login("alice", "password"));
        verify(refresh, never()).issueSession(any(), anyInt(), anyBoolean());
    }
}
