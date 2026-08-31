package kakha.kudava.filedrivespring.controller;

import kakha.kudava.filedrivespring.services.totp.TotpEnrollmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(TotpEnrollmentControllerTests.Config.class)
@WebAppConfiguration
@TestPropertySource(properties = {"ADMIN_PASSWORD=test-password", "app.cors.allowed-origins=http://localhost:5173"})
class TotpEnrollmentControllerDisabledTests {
    @Autowired WebApplicationContext context;
    @Autowired TotpEnrollmentService enrollment;

    @Test
    void endpointsAreDisabledWhenFeatureFlagIsAbsent() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for (String route : new String[]{"/api/mfa/totp/enrollments", "/api/mfa/totp/enrollments/10/confirm"}) {
            mvc.perform(post(route).with(user("alice")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isServiceUnavailable());
        }
        verifyNoInteractions(enrollment);
    }
}
