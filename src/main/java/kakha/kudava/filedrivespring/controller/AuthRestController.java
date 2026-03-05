package kakha.kudava.filedrivespring.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kakha.kudava.filedrivespring.config.SecurityConfig;
import kakha.kudava.filedrivespring.dto.LoginRequest;
import kakha.kudava.filedrivespring.dto.LoginResponse;
import kakha.kudava.filedrivespring.services.JwtService;
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

@RestController
@RequestMapping("/api/auth")
public class AuthRestController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthRestController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @GetMapping("/me")
    public Object me(Authentication auth) {
        return auth == null ? "NOT LOGGED IN" : auth.getName() + " " + auth.getAuthorities();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req, HttpServletRequest request) throws Exception {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword())
        );

        UserDetails user = (UserDetails) auth.getPrincipal();

        String token = jwtService.generateAccessToken(user); // includes roles claim
        return ResponseEntity.ok(new LoginResponse(token));
    }


}
