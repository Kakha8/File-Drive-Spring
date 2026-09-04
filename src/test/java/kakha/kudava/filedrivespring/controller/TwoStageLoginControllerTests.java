package kakha.kudava.filedrivespring.controller;

import kakha.kudava.filedrivespring.config.SecurityConfig;
import kakha.kudava.filedrivespring.dto.LoginResponse;
import kakha.kudava.filedrivespring.exceptions.ApiExceptionHandler;
import kakha.kudava.filedrivespring.repository.UserRepository;
import kakha.kudava.filedrivespring.services.jwt.*;
import kakha.kudava.filedrivespring.services.objects.RootFolderService;
import kakha.kudava.filedrivespring.services.totp.TwoStageLoginService;
import kakha.kudava.filedrivespring.services.users.DbUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(TwoStageLoginControllerTests.Config.class)
@WebAppConfiguration
@TestPropertySource(properties = {"ADMIN_PASSWORD=test", "app.cors.allowed-origins=http://localhost:5173", "JWT_REFRESH_DAYS=7"})
class TwoStageLoginControllerTests {
    @Autowired WebApplicationContext context;
    @Autowired TwoStageLoginService login;
    @Autowired JwtRefreshService refresh;
    MockMvc mvc;

    @BeforeEach void setup() {
        reset(login, refresh);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void passwordStepReturnsChallengeOnlyAndClearsOldCookie() throws Exception {
        when(login.login("alice", "password")).thenReturn(new TwoStageLoginService.LoginResult(null,
                new TwoStageLoginService.MfaRequired(true, "A".repeat(43), Instant.now().plusSeconds(180))));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.challengeToken").value("A".repeat(43)))
                .andExpect(jsonPath("$.accessToken").doesNotExist()).andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test void verificationIsReachableWithoutJwtAndOnlyReturnsCommittedSession() throws Exception {
        when(login.verify("A".repeat(43), "012345")).thenReturn(session());
        mvc.perform(post("/api/auth/mfa/totp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeToken\":\"" + "A".repeat(43) + "\",\"code\":\"012345\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.publicUuid").exists()).andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Set-Cookie", allOf(containsString("refresh_token=refresh"),
                        containsString("Secure"), containsString("HttpOnly"), containsString("SameSite=None"))));
        verify(login).verify("A".repeat(43), "012345");
    }

    @Test void challengeCannotAccessFilesOrRefreshSession() throws Exception {
        mvc.perform(get("/api/files").header("Authorization", "Bearer " + "A".repeat(43)))
                .andExpect(status().isUnauthorized());
        when(refresh.rotate("A".repeat(43), 7)).thenThrow(new JwtRefreshService.RefreshRejected());
        mvc.perform(post("/api/auth/refresh").cookie(new jakarta.servlet.http.Cookie("refresh_token", "A".repeat(43))))
                .andExpect(status().isUnauthorized()).andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test void rejectionDoesNotSetSessionCookiesOrTokens() throws Exception {
        when(login.verify("challenge", "000000")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials."));
        mvc.perform(post("/api/auth/mfa/totp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeToken\":\"challenge\",\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test void malformedCredentialBodiesAreNotEchoed() throws Exception {
        for (String route : new String[]{"/api/auth/login", "/api/auth/mfa/totp"}) {
            mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"private-secret\""))
                    .andExpect(status().isBadRequest()).andExpect(content().string(not(containsString("private-secret"))));
        }
        verifyNoInteractions(login);
    }

    private AuthenticatedSession session() {
        return new AuthenticatedSession(new LoginResponse("access", 1L, "alice", UUID.randomUUID()), "refresh");
    }

    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, AuthRestController.class, RefreshRestController.class, ApiExceptionHandler.class})
    static class Config {
        @Bean TwoStageLoginService login() { return mock(TwoStageLoginService.class); }
        @Bean JwtRefreshService refresh() { return mock(JwtRefreshService.class); }
        @Bean JwtService jwt() { return new JwtService("test-only-signing-key-at-least-32-bytes-long", 15); }
        @Bean DbUserDetailsService users() { return mock(DbUserDetailsService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean RootFolderService rootFolderService() { return mock(RootFolderService.class); }
    }
}
