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
            String displayName, Long existingDeviceId, @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String existingCode) {
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
    @PostMapping(value = "/api/webauthn/registration/options", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WebAuthnService.Options> registrationOptions(Authentication auth, @RequestBody RegistrationStart request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.beginRegistration(
                auth == null ? null : auth.getName(), request.password(), request.displayName(), request.existingDeviceId(), request.existingCode()));
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
        boolean enrollment = request.getServletPath().startsWith("/api/webauthn/registration/")
                || request.getRequestURI().startsWith(request.getContextPath() + "/api/webauthn/registration/");
        int status = enrollment ? 403 : 401;
        String message = enrollment
                ? "Enrollment verification failed. Check your account password and any existing security-key or authenticator verification, then start again."
                : "Invalid or expired WebAuthn request.";
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(ApiErrorResponse.of(enrollment ? "WEBAUTHN_ENROLLMENT_REJECTED" : "WEBAUTHN_REJECTED", message, status));
    }
}
