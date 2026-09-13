package kakha.kudava.filedrivespring.controller;

import kakha.kudava.filedrivespring.config.SecurityConfig;
import kakha.kudava.filedrivespring.dto.LoginResponse;
import kakha.kudava.filedrivespring.exceptions.ApiExceptionHandler;
import kakha.kudava.filedrivespring.repository.UserRepository;
import kakha.kudava.filedrivespring.services.jwt.*;
import kakha.kudava.filedrivespring.services.objects.RootFolderService;
import kakha.kudava.filedrivespring.services.users.DbUserDetailsService;
import kakha.kudava.filedrivespring.services.webauthn.WebAuthnService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(WebAuthnControllerTests.Config.class)
@WebAppConfiguration
@TestPropertySource(properties = {"ADMIN_PASSWORD=test", "app.cors.allowed-origins=http://localhost:5173", "JWT_REFRESH_DAYS=7"})
class WebAuthnControllerTests {
    @Autowired WebApplicationContext context;
    @Autowired WebAuthnService service;
    MockMvc mvc;
    @BeforeEach void setup() { reset(service); mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    @Test void enrollmentRequiresAuthentication() throws Exception {
        for (String route : new String[]{"options", "finish"}) {
            mvc.perform(post("/api/webauthn/registration/" + route).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }
    @Test void enrollmentUsesAuthenticatedOwnerAndReturnsNoStore() throws Exception {
        var options = new WebAuthnService.Options(UUID.randomUUID(), Instant.now().plusSeconds(180), new ObjectMapper().createObjectNode(), null);
        when(service.beginRegistration("alice", "password", "ESP32", null, null)).thenReturn(options);
        mvc.perform(post("/api/webauthn/registration/options").with(user("alice"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"password\",\"displayName\":\"ESP32\",\"userId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestId").exists())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(service).beginRegistration("alice", "password", "ESP32", null, null);
    }
    @Test void loginOptionsArePublicButRequireServiceChallengeValidation() throws Exception {
        when(service.beginLogin("bad")).thenThrow(new WebAuthnService.Rejected());
        mvc.perform(post("/api/auth/webauthn/options").contentType(MediaType.APPLICATION_JSON).content("{\"challengeToken\":\"bad\"}"))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Set-Cookie"));
        verify(service).beginLogin("bad");
    }
    @Test void successfulLoginReturnsOnlyAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
        var id = UUID.randomUUID();
        when(service.finishLogin(eq("challenge"), eq(id), any())).thenReturn(new AuthenticatedSession(
                new LoginResponse("access", 1L, "alice", UUID.randomUUID()), "refresh"));
        mvc.perform(post("/api/auth/webauthn/finish").contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeToken\":\"challenge\",\"requestId\":\"" + id + "\",\"credential\":{}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Set-Cookie", allOf(containsString("HttpOnly"), containsString("Secure"))));
    }
    @Test void malformedJsonIsNotEchoed() throws Exception {
        mvc.perform(post("/api/auth/webauthn/finish").contentType(MediaType.APPLICATION_JSON).content("{\"challengeToken\":\"private-secret\""))
                .andExpect(status().isBadRequest()).andExpect(content().string(not(containsString("private-secret"))));
        verifyNoInteractions(service);
    }
    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, WebAuthnController.class, ApiExceptionHandler.class})
    static class Config {
        @Bean WebAuthnService service() { return mock(WebAuthnService.class); }
        @Bean JwtService jwt() { return new JwtService("test-only-signing-key-at-least-32-bytes-long", 15); }
        @Bean DbUserDetailsService users() { return mock(DbUserDetailsService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean RootFolderService rootFolderService() { return mock(RootFolderService.class); }
    }
}
