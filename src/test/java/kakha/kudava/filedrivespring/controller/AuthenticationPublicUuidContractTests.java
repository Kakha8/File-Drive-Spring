package kakha.kudava.filedrivespring.controller;

import jakarta.servlet.http.Cookie;
import kakha.kudava.filedrivespring.dto.LoginRequest;
import kakha.kudava.filedrivespring.dto.LoginResponse;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.*;
import kakha.kudava.filedrivespring.services.jwt.*;
import kakha.kudava.filedrivespring.services.totp.TwoStageLoginService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationPublicUuidContractTests {
    private static final UUID PUBLIC_UUID = UUID.fromString("8c98baef-9c78-45d3-8797-b27e9786fa26");

    @Test
    void loginReturnsStablePublicUuid() {
        TwoStageLoginService login = mock(TwoStageLoginService.class);
        var session = session();
        when(login.login("alice", "secret")).thenReturn(new TwoStageLoginService.LoginResult(session, null));
        LoginRequest request = new LoginRequest(); request.setUsername("alice"); request.setPassword("secret");
        var response = new AuthRestController(login, 7).login(request, new MockHttpServletResponse());
        LoginResponse body = assertInstanceOf(LoginResponse.class, response.getBody());
        assertEquals("access", body.getAccessToken()); assertEquals(7L, body.getUserId());
        assertEquals("alice", body.getUsername()); assertEquals(PUBLIC_UUID, body.getPublicUuid());
    }

    @Test
    void refreshReturnsSamePublicUuid() {
        JwtRefreshService refresh = mock(JwtRefreshService.class);
        when(refresh.rotate("old-refresh", 7)).thenReturn(session());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refresh_token", "old-refresh"));
        var response = new RefreshRestController(refresh, 7).refresh(request, new MockHttpServletResponse());
        LoginResponse body = assertInstanceOf(LoginResponse.class, response.getBody());
        assertEquals(PUBLIC_UUID, body.getPublicUuid()); assertEquals("access", body.getAccessToken());
    }

    @Test
    void responseCannotBeConstructedWithoutPublicUuid() {
        assertThrows(NullPointerException.class, () -> new LoginResponse("access", 1L, "alice", null));
    }

    @Test
    void accountWithoutPublicUuidFailsBeforeIssuingTokens() {
        JwtService jwt = mock(JwtService.class);
        JwtRefresherRepository tokens = mock(JwtRefresherRepository.class);
        var refresh = new JwtRefreshService(mock(UserRepository.class), jwt, tokens);
        User legacy = new User(); legacy.setId(8L); legacy.setUsername("legacy");
        assertThrows(IllegalStateException.class, () -> refresh.issueSession(legacy, 7, false));
        verifyNoInteractions(jwt, tokens);
    }

    private AuthenticatedSession session() {
        return new AuthenticatedSession(new LoginResponse("access", 7L, "alice", PUBLIC_UUID), "refresh");
    }
}
