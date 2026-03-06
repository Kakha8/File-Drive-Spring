package kakha.kudava.filedrivespring.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kakha.kudava.filedrivespring.model.JwtRefresher;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.services.DbUserDetailsService;
import kakha.kudava.filedrivespring.services.JwtRefreshService;
import kakha.kudava.filedrivespring.services.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth/refresh")
public class RefreshRestController {
    private static final String REFRESH_COOKIE = "refresh_token";

    private final JwtRefreshService refreshService;
    private final JwtService jwtService;

    private final int refreshDays = 14;
    private final DbUserDetailsService dbUserDetailsService;

    public RefreshRestController(JwtRefreshService refreshService,
                                 JwtService jwtService, DbUserDetailsService dbUserDetailsService) {
        this.refreshService = refreshService;
        this.jwtService = jwtService;
        this.dbUserDetailsService = dbUserDetailsService;
    }


    @PostMapping
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {

        System.out.println("HIT /api/auth/refresh");

        String rawRefresh = readCookie(request, REFRESH_COOKIE);
        if (rawRefresh == null || rawRefresh.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Missing refresh token"));
        }

        JwtRefresher stored;
        try {
            stored = refreshService.validateToken(rawRefresh);
        } catch (RuntimeException ex) {
            // invalid / expired / revoked
            clearRefreshCookie(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", ex.getMessage()));
        }

        // rotating refresh token
        refreshService.revoke(stored);

        User user = stored.getUser();
        UserDetails userDetails = dbUserDetailsService.loadUserByUsername(user.getUsername());
        String newAccessToken = jwtService.generateAccessToken(userDetails);

        log.info("[+]New refresh token issued");
        return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
    }


    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private void setRefreshCookie(HttpServletResponse response, String token, int days) {
        // Prefer Set-Cookie header to include SameSite reliably
        int maxAge = (int) Duration.ofDays(days).getSeconds();

        log.info("Setting refresh cookie days={}, maxAgeSeconds={}", days, maxAge);

        response.addHeader("Set-Cookie",
                REFRESH_COOKIE + "=" + token
                        + "; Max-Age=" + maxAge
                        + "; Path=/"
                        + "; HttpOnly"
                        + "; SameSite=Lax");
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie",
                REFRESH_COOKIE + "=; Max-Age=0; Path=/api/auth; Secure; HttpOnly; SameSite=None");
    }
}
