package kakha.kudava.filedrivespring.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kakha.kudava.filedrivespring.services.jwt.JwtRefreshService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/refresh")
public class RefreshRestController {
    private final JwtRefreshService refreshService;
    private final int refreshDays;

    public RefreshRestController(JwtRefreshService refreshService, @Value("${JWT_REFRESH_DAYS}") int refreshDays) {
        this.refreshService = refreshService;
        this.refreshDays = refreshDays;
    }

    @PostMapping
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        String raw = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refresh_token".equals(cookie.getName())) { raw = cookie.getValue(); break; }
            }
        }
        try {
            var session = refreshService.rotate(raw, refreshDays);
            AuthCookies.setRefresh(response, session.refreshToken(), refreshDays);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(session.login());
        } catch (JwtRefreshService.RefreshRejected rejected) {
            AuthCookies.clearRefresh(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore())
                    .body(Map.of("message", "Invalid or expired refresh session. Log in again."));
        }
    }
}
