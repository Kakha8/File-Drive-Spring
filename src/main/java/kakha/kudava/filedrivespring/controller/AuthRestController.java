package kakha.kudava.filedrivespring.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kakha.kudava.filedrivespring.config.SecurityConfig;
import kakha.kudava.filedrivespring.dto.LoginRequest;
import kakha.kudava.filedrivespring.dto.LoginResponse;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.services.JwtRefreshService;
import kakha.kudava.filedrivespring.services.JwtService;
import kakha.kudava.filedrivespring.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthRestController {


    private static final String REFRESH_COOKIE = "refresh_token";
    private final int refreshDays;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtRefreshService refreshService;
    private final UserService userService;

    public AuthRestController(@Value("${JWT_REFRESH_DAYS}") int refreshDays,
                              AuthenticationManager authenticationManager,
                              JwtService jwtService,
                              JwtRefreshService refreshService, UserService userService) {

        this.refreshDays = refreshDays;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshService = refreshService;
        this.userService = userService;
    }

    @GetMapping("/me")
    public Object me(Authentication auth) {
        return auth == null ? "NOT LOGGED IN" : auth.getName() + " " + auth.getAuthorities();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req, HttpServletResponse response) throws Exception {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword())
        );

        UserDetails user = (UserDetails) auth.getPrincipal();

        String username = user.getUsername();
        User selectedUser = userService.getUserByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String token = jwtService.generateAccessToken(user); // includes roles claim

        String refreshToken = refreshService.createToken(selectedUser, refreshDays);
        setRefreshCookie(response, refreshToken, refreshDays);

        return ResponseEntity.ok(new LoginResponse(token));
    }

    private void setRefreshCookie(HttpServletResponse response, String token, int days) {
        int maxAge = (int) Duration.ofDays(days).getSeconds();

        log.info("Setting refresh cookie days={}, maxAgeSeconds={}", days, maxAge);

        response.addHeader("Set-Cookie",
                REFRESH_COOKIE + "=" + token
                        + "; Max-Age=" + maxAge
                        + "; Path=/api/auth"
                        + "; HttpOnly"
                        + "; SameSite=Lax");
    }


}
