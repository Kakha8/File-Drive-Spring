package kakha.kudava.filedrivespring.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import kakha.kudava.filedrivespring.dto.LoginResponse;
import kakha.kudava.filedrivespring.records.ApiErrorResponse;
import kakha.kudava.filedrivespring.services.webauthn.WebAuthnService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class WebAuthnController {
    private final WebAuthnService service;
    private final int refreshDays;
    public WebAuthnController(WebAuthnService service, @Value("${JWT_REFRESH_DAYS}") int refreshDays) {
        this.service = service; this.refreshDays = refreshDays;
    }
    public record RegistrationStart(@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
            String displayName) {
        @Override public String toString() { return "RegistrationStart[redacted]"; }
    }
    public record RegistrationFinish(UUID requestId, JsonNode credential, JsonNode authorizationCredential) {
        @Override public String toString() { return "RegistrationFinish[redacted]"; }
    }
    public record LoginStart(@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String challengeToken) {
        @Override public String toString() { return "LoginStart[redacted]"; }
    }
    public record LoginFinish(@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String challengeToken, UUID requestId, JsonNode credential) {
        @Override public String toString() { return "LoginFinish[redacted]"; }
    }
    public record RemovalStart(@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {
        @Override public String toString() { return "RemovalStart[redacted]"; }
    }
    public record RemovalFinish(UUID requestId, JsonNode authorizationCredential) {
        @Override public String toString() { return "RemovalFinish[redacted]"; }
    }
    @PostMapping(value = "/api/webauthn/registration/options", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WebAuthnService.Options> registrationOptions(Authentication auth, @RequestBody RegistrationStart request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.beginRegistration(
                auth == null ? null : auth.getName(), request.password(), request.displayName()));
    }
    @GetMapping(value = "/api/webauthn/credentials", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WebAuthnService.CredentialStatus> credentials(Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.credentialStatus(auth == null ? null : auth.getName()));
    }
    @PostMapping(value = "/api/webauthn/credentials/{credentialId}/removal/options", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WebAuthnService.RemovalOptions> removalOptions(Authentication auth,
            @PathVariable Long credentialId, @RequestBody RemovalStart request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.beginRemoval(
                auth == null ? null : auth.getName(), credentialId, request.password()));
    }
    @PostMapping(value = "/api/webauthn/credentials/{credentialId}/removal/finish", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WebAuthnService.Removed> removalFinish(Authentication auth,
            @PathVariable Long credentialId, @RequestBody RemovalFinish request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.finishRemoval(
                auth == null ? null : auth.getName(), credentialId, request.requestId(),
                request.authorizationCredential()));
    }
    @PostMapping(value = "/api/webauthn/registration/finish", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WebAuthnService.Registered> registrationFinish(Authentication auth, @RequestBody RegistrationFinish request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.finishRegistration(
                auth == null ? null : auth.getName(), request.requestId(), request.credential(), request.authorizationCredential()));
    }
    @PostMapping(value = "/api/auth/webauthn/options", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WebAuthnService.Options> loginOptions(@RequestBody LoginStart request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.beginLogin(request.challengeToken()));
    }
    @PostMapping(value = "/api/auth/webauthn/finish", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LoginResponse> loginFinish(@RequestBody LoginFinish request, HttpServletResponse response) {
        var session = service.finishLogin(request.challengeToken(), request.requestId(), request.credential());
        AuthCookies.setRefresh(response, session.refreshToken(), refreshDays);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(session.login());
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> malformedRequest() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(ApiErrorResponse.of("INVALID_WEBAUTHN_REQUEST", "Invalid WebAuthn request.", 400));
    }

    @ExceptionHandler(WebAuthnService.Rejected.class)
    public ResponseEntity<ApiErrorResponse> rejected(HttpServletRequest request) {
        // These routes already require an authenticated session. A rejected
        // ceremony must not trigger the client's token refresh/logout logic.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean enrollment = path.startsWith("/api/webauthn/registration/");
        boolean management = enrollment || path.startsWith("/api/webauthn/credentials/");
        int status = management ? 403 : 401;
        String message = enrollment
                ? "Enrollment verification failed. Check your account password and existing security key, then start again."
                : management ? "Security-key removal verification failed. Check your account password and verification method, then start again."
                : "Invalid or expired WebAuthn request.";
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(ApiErrorResponse.of(enrollment ? "WEBAUTHN_ENROLLMENT_REJECTED"
                        : management ? "WEBAUTHN_REMOVAL_REJECTED" : "WEBAUTHN_REJECTED", message, status));
    }
}
