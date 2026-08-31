package kakha.kudava.filedrivespring.controller;

import jakarta.servlet.http.HttpServletResponse;
import kakha.kudava.filedrivespring.dto.LoginRequest;
import kakha.kudava.filedrivespring.dto.LoginResponse;
import kakha.kudava.filedrivespring.dto.totp.MfaLoginRequest;
import kakha.kudava.filedrivespring.records.ApiErrorResponse;
import kakha.kudava.filedrivespring.services.totp.TwoStageLoginService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthRestController {
    private final TwoStageLoginService loginService;
    private final int refreshDays;

    public AuthRestController(TwoStageLoginService loginService, @Value("${JWT_REFRESH_DAYS}") int refreshDays) {
        this.loginService = loginService;
        this.refreshDays = refreshDays;
    }

    @GetMapping("/me")
    public Object me(Authentication auth) {
        return auth == null ? "NOT LOGGED IN" : auth.getName() + " " + auth.getAuthorities();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        var result = loginService.login(request.getUsername(), request.getPassword());
        // The service proxy has committed before any token is written to the response.
        if (result.challenge() != null) {
            AuthCookies.clearRefresh(response);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result.challenge());
        }
        AuthCookies.setRefresh(response, result.session().refreshToken(), refreshDays);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result.session().login());
    }

    @PostMapping("/mfa/totp")
    public ResponseEntity<LoginResponse> verify(@RequestBody MfaLoginRequest request, HttpServletResponse response) {
        var session = loginService.verify(request.challengeToken(), request.code());
        AuthCookies.setRefresh(response, session.refreshToken(), refreshDays);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(session.login());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> malformedRequest() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(ApiErrorResponse.of("INVALID_AUTH_REQUEST", "Invalid authentication request.", 400));
    }
}
