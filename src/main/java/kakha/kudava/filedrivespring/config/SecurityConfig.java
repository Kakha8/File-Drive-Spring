package kakha.kudava.filedrivespring.config;

import kakha.kudava.filedrivespring.repository.UserRepository;
import kakha.kudava.filedrivespring.services.DbUserDetailsService;
import kakha.kudava.filedrivespring.services.JwtFilter;
import kakha.kudava.filedrivespring.services.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
public class SecurityConfig {

    @Value("${ADMIN_PASSWORD}")
    private String ADMIN_PASSWORD;

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                                      JwtService jwtService,
                                                      DbUserDetailsService userDetailService) throws Exception {
        http.csrf(csrf -> csrf.disable());

        http
                .securityContext(sc -> sc
                        .securityContextRepository(new HttpSessionSecurityContextRepository())
                )
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/h2-console/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))

                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))


                .authorizeHttpRequests(auth -> auth

                        .requestMatchers("/h2-console/**").permitAll() // temporary. gonna change it to admin

                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/auth/logout").permitAll()
                        .requestMatchers("/api/auth/me").permitAll()
                        .requestMatchers("/api/auth/refresh").permitAll()

                .requestMatchers("/api/files").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/folders/**").hasAnyRole("USER", "ADMIN")

                .anyRequest().authenticated()
        );

        http.addFilterBefore(new JwtFilter(jwtService, userDetailService),
                UsernamePasswordAuthenticationFilter.class);

        http.logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
        );
        return http.build();
    }

    @Bean
    CommandLineRunner createAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByUsername("admin").isEmpty()) {
                kakha.kudava.filedrivespring.model.User admin = new kakha.kudava.filedrivespring.model.User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
                admin.setRole(kakha.kudava.filedrivespring.model.User.Role.ADMIN);
                userRepository.save(admin);
            }
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
