package kakha.kudava.filedrivespring.services.webauthn;

import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.util.*;

@Component
public class WebAuthnEngine {
    private final RelyingParty rp;
    public WebAuthnEngine(WebAuthnCredentials credentials,
            @Value("${app.webauthn.enabled:false}") boolean enabled,
            @Value("${app.webauthn.rp-id:}") String rpId,
            @Value("${app.webauthn.origins:}") String origins) {
        if (!enabled) { rp = null; return; }
        if (rpId.isBlank() || !rpId.equals(rpId.toLowerCase(Locale.ROOT)) || rpId.contains(":") || rpId.contains("/"))
            throw new IllegalArgumentException("Configure a lowercase WebAuthn RP domain.");
        Set<String> allowed = new HashSet<>();
        for (String s : origins.split(",")) {
            String origin = s.trim();
            URI uri = URI.create(origin);
            String host = uri.getHost();
            boolean secure = "https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && "localhost".equals(host));
            if (!secure || host == null || !(host.equals(rpId) || host.endsWith("." + rpId))
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty()))
                throw new IllegalArgumentException("Configure exact HTTPS WebAuthn origins (HTTP localhost is allowed for development).");
            allowed.add(origin);
        }
        if (allowed.isEmpty()) throw new IllegalArgumentException("WebAuthn origins are required.");
        rp = RelyingParty.builder().identity(RelyingPartyIdentity.builder().id(rpId).name("File Drive").build())
                .credentialRepository(credentials).origins(allowed)
                .allowOriginPort(false).allowOriginSubdomain(false)
                .validateSignatureCounter(false).attestationConveyancePreference(AttestationConveyancePreference.NONE)
                .build();
    }
    public RelyingParty relyingParty() {
        if (rp == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "WebAuthn is not configured.");
        return rp;
    }
}
