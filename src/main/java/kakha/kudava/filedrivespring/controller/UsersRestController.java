package kakha.kudava.filedrivespring.controller;

import kakha.kudava.filedrivespring.dto.UserDTO;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.UserRepository;
import kakha.kudava.filedrivespring.services.users.RegistrationService;
import kakha.kudava.filedrivespring.services.users.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UsersRestController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final RegistrationService registrationService;

    public UsersRestController(UserService userService, UserRepository userRepository, RegistrationService registrationService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.registrationService = registrationService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<UserDTO>> getUsers() {
        List<UserDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, String>> getUser(@PathVariable Long id) {
        Optional<User> user = userService.findById(id);
        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        } else
            return ResponseEntity.ok(Map.of(
                    "id", String.valueOf(user.get().getId()),
                    "username", user.get().getUsername()));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @GetMapping("/search")
    public List<UserDTO> searchUsers(@RequestParam(name = "q", defaultValue = "") String q,
                                     Authentication authentication) {
        String query = q.trim();

        if (query.isBlank()) {
            return List.of();
        }

        String currentUsername = authentication.getName();

        return userRepository.findByUsernameContainingIgnoreCase(query)
                .stream()
                .filter(user -> !user.isDeleted())
                .filter(user -> !user.getUsername().equals(currentUsername))
                .limit(10)
                .map(user -> {
                    UserDTO dto = new UserDTO();
                    dto.setId(user.getId());
                    dto.setUsername(user.getUsername());
                    return dto;
                })
                .toList();
    }

/*    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Map<String, String>> addUser(@RequestBody UserDTO userDTO) {
        UserDTO savedUser = userService.saveUser(userDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("Message" , "User added successfully!" ,
                "Username", savedUser.getUsername() )
        );
    }*/

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest request,
                                              Authentication authentication) {
        return ResponseEntity.ok(userService.update(id, request.username(), request.role(), authentication.getName()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/suspension")
    public ResponseEntity<UserDTO> setSuspended(@PathVariable Long id,
                                                @RequestBody SuspensionRequest request,
                                                Authentication authentication) {
        UserService.SuspensionDuration duration;
        try {
            duration = UserService.SuspensionDuration.valueOf(
                    request.duration() == null ? "INDEFINITE" : request.duration().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid suspension duration");
        }
        return ResponseEntity.ok(userService.setSuspended(id, request.suspended(), duration, authentication.getName()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, Authentication authentication) {
        userService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/restore")
    public ResponseEntity<UserDTO> restoreUser(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(userService.restore(id, authentication.getName()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/password-reset")
    public ResponseEntity<UserDTO> resetPassword(@PathVariable Long id,
                                                 @RequestBody PasswordResetRequest request,
                                                 Authentication authentication) {
        return ResponseEntity.ok(userService.resetPassword(id, request.temporaryPassword(), authentication.getName()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}/two-factor")
    public ResponseEntity<UserDTO> removeTwoFactor(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(userService.removeTwoFactor(id, authentication.getName()));
    }

    public record UpdateUserRequest(String username, String role) {}
    public record SuspensionRequest(boolean suspended, String duration) {}
    public record PasswordResetRequest(String temporaryPassword) {}

}
