package kakha.kudava.filedrivespring.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kakha.kudava.filedrivespring.config.SecurityConfig;
import kakha.kudava.filedrivespring.dto.totp.TotpEnrollmentConfirmRequest;
import kakha.kudava.filedrivespring.dto.totp.TotpEnrollmentRequest;
import kakha.kudava.filedrivespring.exceptions.ApiExceptionHandler;
import kakha.kudava.filedrivespring.repository.UserRepository;
import kakha.kudava.filedrivespring.services.jwt.JwtService;
import kakha.kudava.filedrivespring.services.objects.RootFolderService;
import kakha.kudava.filedrivespring.services.totp.TotpEnrollmentService;
import kakha.kudava.filedrivespring.services.users.DbUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(TotpEnrollmentControllerTests.Config.class)
@WebAppConfiguration
@TestPropertySource(properties = {"ADMIN_PASSWORD=test-password", "app.cors.allowed-origins=http://localhost:5173",
        "app.totp.enrollment-api-enabled=true"})
class TotpEnrollmentControllerTests {
    private static final String PATH = "/api/mfa/totp/enrollments";
    private static final String SEED = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    @Autowired WebApplicationContext context;
    @Autowired TotpEnrollmentService enrollment;
    @Autowired DbUserDetailsService users;
    @Autowired JwtService jwt;
    private MockMvc mvc;
    private String authorization;

    @BeforeEach
    void setup() {
        reset(enrollment, users);
        var user = User.withUsername("alice").password("unused").roles("USER").build();
        when(users.loadUserByUsername("alice")).thenReturn(user);
        authorization = "Bearer " + jwt.generateAccessToken(user);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void firstEnrollmentUsesAuthenticatedSessionAndReturnsSafeProjection() throws Exception {
        when(enrollment.begin("ESP32", SEED, "password", null, null))
                .thenReturn(new TotpEnrollmentService.Enrollment(10L, "ESP32", Instant.parse("2026-08-31T00:05:00Z")));
        mvc.perform(post(PATH).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"ESP32", "secretBase32":"%s", "password":"password", "userId":999}
                                """.formatted(SEED)))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.deviceId").value(10)).andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.secretBase32").doesNotExist()).andExpect(jsonPath("$.encryptedSecret").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.accessToken").doesNotExist());
        verify(enrollment).begin("ESP32", SEED, "password", null, null);
    }

    @Test
    void additionalDeviceForwardsExistingFactorCredentials() throws Exception {
        mvc.perform(post(PATH).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"ESP32", "secretBase32":"%s", "password":"password",
                         "existingDeviceId":7, "existingCode":"012345"}
                        """.formatted(SEED))).andExpect(status().isCreated());
        verify(enrollment).begin("ESP32", SEED, "password", 7L, "012345");
    }

    @Test
    void confirmationPreservesLeadingZerosAndDoesNotIssueTokens() throws Exception {
        when(enrollment.confirm(10L, "012345")).thenReturn(new TotpEnrollmentService.Confirmation(
                10L, "ESP32", Instant.parse("2026-08-31T00:01:00Z")));
        mvc.perform(post(PATH + "/10/confirm").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"012345\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.deviceId").value(10)).andExpect(jsonPath("$.confirmedAt").exists())
                .andExpect(jsonPath("$.code").doesNotExist()).andExpect(jsonPath("$.accessToken").doesNotExist());
        verify(enrollment).confirm(10L, "012345");
    }

    @Test
    void statusReturnsOnlyTheAuthenticatedAccountsEnabledFlag() throws Exception {
        when(enrollment.status()).thenReturn(new TotpEnrollmentService.Status(true,
                List.of(new TotpEnrollmentService.DeviceSummary(7L, "Ledger Nano"))));

        mvc.perform(get(PATH + "/status"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH + "/status").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.devices[0].deviceId").value(7))
                .andExpect(jsonPath("$.devices[0].displayName").value("Ledger Nano"))
                .andExpect(jsonPath("$.secretBase32").doesNotExist());
    }

    @Test
    void removalForwardsOnlyAuthenticatedDeviceProofAndReturnsSafeResult() throws Exception {
        when(enrollment.remove(7L, "password", 8L, "012345"))
                .thenReturn(new TotpEnrollmentService.Removal(7L, true, 1));
        mvc.perform(delete(PATH + "/devices/7").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password\",\"authorizingDeviceId\":8,\"code\":\"012345\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.removedDeviceId").value(7))
                .andExpect(jsonPath("$.remainingDevices").value(1))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void bothEndpointsRequireAuthentication() throws Exception {
        for (String route : new String[]{PATH, PATH + "/10/confirm"}) {
            mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(route).header("Authorization", "Bearer invalid").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(enrollment);
    }

    @Test
    void preservesServiceRejectionStatus() throws Exception {
        for (HttpStatus status : new HttpStatus[]{HttpStatus.BAD_REQUEST, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND,
                HttpStatus.CONFLICT, HttpStatus.GONE, HttpStatus.TOO_MANY_REQUESTS}) {
            doThrow(new ResponseStatusException(status, "Enrollment rejected.")).when(enrollment).confirm(10L, "000000");
            mvc.perform(post(PATH + "/10/confirm").header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"000000\"}"))
                    .andExpect(status().is(status.value()));
        }
    }

    @Test
    void malformedBodiesAndPathsReturnGenericErrorsWithoutEchoingInput() throws Exception {
        for (String body : new String[]{"", "null", "{", "{\"existingDeviceId\":\"sensitive-value\"}"}) {
            mvc.perform(post(PATH).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(content().string(not(containsString("sensitive-value"))));
        }
        mvc.perform(post(PATH + "/sensitive-value/confirm").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(content().string(not(containsString("sensitive-value"))));
        mvc.perform(post(PATH + "/0/confirm").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(enrollment);
    }

    @Test
    void requestDtosRedactSecretsFromSerializationAndToString() throws Exception {
        var request = new TotpEnrollmentRequest("ESP32", SEED, "sensitive-password", 7L, "012345");
        String json = new ObjectMapper().writeValueAsString(request);
        assertFalse(json.contains(SEED));
        assertFalse(json.contains("sensitive-password"));
        assertFalse(json.contains("012345"));
        assertEquals("TotpEnrollmentRequest[redacted]", request.toString());
        var confirm = new TotpEnrollmentConfirmRequest("012345");
        assertFalse(new ObjectMapper().writeValueAsString(confirm).contains("012345"));
        assertEquals("TotpEnrollmentConfirmRequest[redacted]", confirm.toString());
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, TotpEnrollmentController.class, ApiExceptionHandler.class})
    static class Config {
        @Bean TotpEnrollmentService enrollment() { return mock(TotpEnrollmentService.class); }
        @Bean JwtService jwt() { return new JwtService("test-only-signing-key-at-least-32-bytes-long", 15); }
        @Bean DbUserDetailsService users() { return mock(DbUserDetailsService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean RootFolderService rootFolderService() { return mock(RootFolderService.class); }
    }
}
