package kakha.kudava.filedrivespring.config;

import kakha.kudava.filedrivespring.repository.UserRepository;
import kakha.kudava.filedrivespring.services.jwt.JwtService;
import kakha.kudava.filedrivespring.services.objects.RootFolderService;
import kakha.kudava.filedrivespring.services.users.DbUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(AuthenticationStatusTests.TestConfig.class)
@WebAppConfiguration
@TestPropertySource(properties = {
        "ADMIN_PASSWORD=test-only-password",
        "app.cors.allowed-origins=http://localhost:5173"
})
class AuthenticationStatusTests {
    private static final String SECRET = "test-only-signing-key-at-least-32-bytes-long";
    private static final UserDetails USER = User.withUsername("alice")
            .password("unused").roles("USER").build();

    @Autowired WebApplicationContext context;
    @Autowired DbUserDetailsService users;
    @Autowired JwtService jwt;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(users.loadUserByUsername("alice")).thenReturn(USER);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/folders/root")).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenReturnsUnauthorizedSoClientCanRefresh() throws Exception {
        String expiredToken = new JwtService(SECRET, -1).generateAccessToken(USER);
        mvc.perform(get("/api/folders/root").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/folders/root").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenCanAccessFolders() throws Exception {
        mvc.perform(get("/api/folders/root").header("Authorization", "Bearer " + jwt.generateAccessToken(USER)))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedUserWithoutRequiredRoleStillGetsForbidden() throws Exception {
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + jwt.generateAccessToken(USER)))
                .andExpect(status().isForbidden());
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean JwtService jwtService() { return new JwtService(SECRET, 15); }
        @Bean DbUserDetailsService users() { return mock(DbUserDetailsService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean RootFolderService rootFolderService() { return mock(RootFolderService.class); }
        @Bean TestEndpoints endpoints() { return new TestEndpoints(); }
    }

    @RestController
    static class TestEndpoints {
        @GetMapping({"/api/folders/root", "/api/users"})
        String protectedResource() { return "ok"; }
    }
}
