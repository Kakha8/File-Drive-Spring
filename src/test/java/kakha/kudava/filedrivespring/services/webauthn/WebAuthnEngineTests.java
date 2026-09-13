package kakha.kudava.filedrivespring.services.webauthn;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebAuthnEngineTests {
    final WebAuthnCredentials credentials = mock(WebAuthnCredentials.class);
    @Test void localhostAllowsExactDevelopmentOriginAndDisablesCounterValidation() {
        var rp = new WebAuthnEngine(credentials, true, "localhost", "http://localhost:5173").relyingParty();
        assertEquals(java.util.Set.of("http://localhost:5173"), rp.getOrigins());
        assertFalse(rp.isAllowOriginPort()); assertFalse(rp.isAllowOriginSubdomain());
        assertFalse(rp.isValidateSignatureCounter());
    }
    @Test void invalidOriginsFailStartup() {
        for (String origin : new String[]{"", "http://example.com", "https://evil.example", "https://example.com/", "https://example.com?x=1", "https://user@example.com"}) {
            assertThrows(IllegalArgumentException.class, () -> new WebAuthnEngine(credentials, true, "example.com", origin));
        }
    }
    @Test void disabledModeFailsClosed() {
        var engine = new WebAuthnEngine(credentials, false, "", "");
        assertEquals(503, assertThrows(ResponseStatusException.class, engine::relyingParty).getStatusCode().value());
    }
}
