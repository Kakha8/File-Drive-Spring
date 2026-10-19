package kakha.kudava.filedrivespring.services.users;

import jakarta.transaction.Transactional;
import kakha.kudava.filedrivespring.dto.UserDTO;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.UserRepository;
import kakha.kudava.filedrivespring.repository.JwtRefresherRepository;
import kakha.kudava.filedrivespring.repository.MfaLoginChallengeRepository;
import kakha.kudava.filedrivespring.repository.TotpDeviceRepository;
import kakha.kudava.filedrivespring.repository.WebAuthnCeremonyRepository;
import kakha.kudava.filedrivespring.repository.WebAuthnCredentialRepository;
import kakha.kudava.filedrivespring.services.objects.RootFolderService;
import kakha.kudava.filedrivespring.services.LogsService;
import kakha.kudava.filedrivespring.enums.ActionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Duration;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RootFolderService rootFolderService;
    private final PasswordEncoder passwordEncoder;
    private final LogsService logsService;
    private final JwtRefresherRepository refreshTokens;
    private final MfaLoginChallengeRepository mfaChallenges;
    private final TotpDeviceRepository totpDevices;
    private final WebAuthnCredentialRepository webauthnCredentials;
    private final WebAuthnCeremonyRepository webauthnCeremonies;

    public UserService(UserRepository userRepository, RootFolderService rootFolderService, PasswordEncoder passwordEncoder,
                       LogsService logsService, JwtRefresherRepository refreshTokens,
                       MfaLoginChallengeRepository mfaChallenges, TotpDeviceRepository totpDevices,
                       WebAuthnCredentialRepository webauthnCredentials,
                       WebAuthnCeremonyRepository webauthnCeremonies) {
        this.userRepository = userRepository;
        this.rootFolderService = rootFolderService;
        this.passwordEncoder = passwordEncoder;
        this.logsService = logsService;
        this.refreshTokens = refreshTokens;
        this.mfaChallenges = mfaChallenges;
        this.totpDevices = totpDevices;
        this.webauthnCredentials = webauthnCredentials;
        this.webauthnCeremonies = webauthnCeremonies;
    }

/*    public UserDTO saveUser(UserDTO userDTO) {
        User user = new User();
        user.setUsername(userDTO.getUsername());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setRole(User.Role.valueOf(userDTO.getRole()));
        User saved = userRepository.save(user);

        rootFolderService.ensureRootFolder(saved);

        log.info("User saved successfully: {}", userDTO.getUsername());
        return userDTO;
    }*/

    public List<UserDTO> getAllUsers() {
        List<User> users = userRepository.findAll();
        List<UserDTO> userDTOs = new ArrayList<>();
        for (User user : users) {
            UserDTO userDTO = new UserDTO();
            userDTO.setId(user.getId());
            userDTO.setUsername(user.getUsername());
            userDTO.setRole(user.getRole() == null ? User.Role.USER.name() : user.getRole().name());
            userDTO.setPasswordChangeRequired(user.isPasswordChangeRequired());
            userDTO.setSuspended(user.isSuspensionActive());
            userDTO.setSuspendedUntil(user.isSuspensionActive() ? user.getSuspendedUntil() : null);
            userDTO.setDeleted(user.isDeleted());
            userDTO.setDeletedAt(user.getDeletedAt());
            userDTO.setCreatedAt(user.getCreatedAt());
            userDTO.setTotpEnabled(user.isTotpEnabled());
            userDTO.setWebauthnEnabled(user.isWebauthnEnabled());

            userDTOs.add(userDTO);
        }
        log.info("Retuning all users from database...");
        return userDTOs;
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByEmail(String userName) {
        return userRepository.findByUsername(userName);
    }

    @Transactional
    public UserDTO update(Long id, String username, String role, String actingUsername) {
        User user = required(id);
        if (user.isDeleted()) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "Deleted accounts cannot be edited");
        String normalizedUsername = username == null ? "" : username.trim();
        if (normalizedUsername.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Username is required");
        }
        if (user.getUsername().equals(actingUsername) && !normalizedUsername.equals(user.getUsername())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "You cannot rename your own active account");
        }
        userRepository.findByUsername(normalizedUsername)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT, "Username is already in use"); });
        User.Role normalizedRole;
        try {
            normalizedRole = User.Role.valueOf(role == null ? "USER" : role.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid role");
        }
        if (user.getUsername().equals(actingUsername) && normalizedRole != user.getRole()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "You cannot change your own role");
        }
        user.setUsername(normalizedUsername);
        user.setRole(normalizedRole);
        return toDto(userRepository.save(user));
    }

    @Transactional
    public UserDTO setSuspended(Long id, boolean suspended, SuspensionDuration duration, String actingUsername) {
        User user = required(id);
        if (user.isDeleted()) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "Deleted accounts cannot be suspended");
        if (user.getUsername().equals(actingUsername)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "You cannot suspend your own account");
        }
        user.setSuspended(suspended);
        if (suspended) {
            Instant now = Instant.now();
            user.setSuspendedAt(now);
            user.setSuspendedUntil(duration == SuspensionDuration.INDEFINITE ? null : now.plus(duration.duration()));
        } else {
            user.setSuspendedAt(null);
            user.setSuspendedUntil(null);
        }
        User saved = userRepository.save(user);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", suspended ? "SUSPENDED" : "ACTIVE");
        if (suspended) {
            details.put("duration", duration.name());
            if (user.getSuspendedUntil() != null) details.put("suspendedUntil", user.getSuspendedUntil());
        }
        logsService.userAccountLog(
                suspended ? ActionType.USER_SUSPEND : ActionType.USER_REACTIVATE,
                saved,
                details
        );
        return toDto(saved);
    }

    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void reactivateExpiredSuspensions() {
        userRepository.reactivateExpiredSuspensions(Instant.now());
    }

    private User required(Long id) {
        return userRepository.findById(id).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));
    }

    private static UserDTO toDto(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setRole(user.getRole() == null ? User.Role.USER.name() : user.getRole().name());
        dto.setPasswordChangeRequired(user.isPasswordChangeRequired());
        dto.setSuspended(user.isSuspensionActive());
        dto.setSuspendedUntil(user.isSuspensionActive() ? user.getSuspendedUntil() : null);
        dto.setDeleted(user.isDeleted());
        dto.setDeletedAt(user.getDeletedAt());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setTotpEnabled(user.isTotpEnabled());
        dto.setWebauthnEnabled(user.isWebauthnEnabled());
        return dto;
    }

    public enum SuspensionDuration {
        ONE_DAY(Duration.ofDays(1)),
        THREE_DAYS(Duration.ofDays(3)),
        ONE_WEEK(Duration.ofDays(7)),
        INDEFINITE(Duration.ZERO);

        private final Duration duration;
        SuspensionDuration(Duration duration) { this.duration = duration; }
        public Duration duration() { return duration; }
    }

    @Transactional
    public void delete(Long id, String actingUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        if (user.getUsername().equals(actingUsername)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "You cannot delete your own account"
            );
        }

        if (!user.isDeleted()) {
            user.setDeleted(true);
            user.setDeletedAt(Instant.now());
            user.setSuspended(false);
            user.setSuspendedAt(null);
            user.setSuspendedUntil(null);
            User saved = userRepository.save(user);
            logsService.userAccountLog(ActionType.USER_DELETE, saved, Map.of("status", "DELETED"));
        }
        log.info("User soft-deleted successfully {}", id);
    }

    @Transactional
    public UserDTO restore(Long id, String actingUsername) {
        User user = required(id);
        if (!user.isDeleted()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Account is not deleted");
        }
        if (user.getUsername().equals(actingUsername)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Your active account cannot be restored");
        }

        user.setDeleted(false);
        user.setDeletedAt(null);
        User saved = userRepository.save(user);
        logsService.userAccountLog(ActionType.USER_RESTORE, saved, Map.of("status", "ACTIVE"));
        log.info("User restored successfully {}", id);
        return toDto(saved);
    }

    @Transactional
    public UserDTO resetPassword(Long id, String temporaryPassword, String actingUsername) {
        User user = manageableUser(id, actingUsername);
        if (temporaryPassword == null || temporaryPassword.length() < 8 || temporaryPassword.length() > 1024) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Temporary password must contain at least 8 characters");
        }

        boolean hadTwoFactor = user.isTotpEnabled() || user.isWebauthnEnabled();
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setPasswordChangeRequired(true);
        removeAuthenticationFactors(user);
        refreshTokens.revokeAllActiveByUserId(user.getId());
        User saved = userRepository.save(user);
        logsService.userAccountLog(ActionType.USER_PASSWORD_RESET, saved,
                Map.of("passwordChangeRequired", true, "twoFactorRemoved", hadTwoFactor));
        return toDto(saved);
    }

    @Transactional
    public UserDTO removeTwoFactor(Long id, String actingUsername) {
        User user = manageableUser(id, actingUsername);
        boolean hadTotp = user.isTotpEnabled();
        boolean hadWebauthn = user.isWebauthnEnabled();
        removeAuthenticationFactors(user);
        refreshTokens.revokeAllActiveByUserId(user.getId());
        User saved = userRepository.save(user);
        logsService.userAccountLog(ActionType.USER_2FA_REMOVE, saved,
                Map.of("totpRemoved", hadTotp, "webauthnRemoved", hadWebauthn));
        return toDto(saved);
    }

    private User manageableUser(Long id, String actingUsername) {
        User user = required(id);
        if (user.isDeleted()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Restore the account before changing its security settings");
        }
        if (user.getUsername().equals(actingUsername)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Manage your own security from account settings");
        }
        return user;
    }

    private void removeAuthenticationFactors(User user) {
        mfaChallenges.consumeOutstanding(user.getId(), Instant.now());
        webauthnCeremonies.deleteAllByUserId(user.getId());
        webauthnCredentials.deleteAllByUserId(user.getId());
        totpDevices.deleteAllByUserId(user.getId());
        user.setTotpEnabled(false);
        user.setWebauthnEnabled(false);
    }

    public List<User> getUsers() {
        return userRepository.findAll();
    }
}
