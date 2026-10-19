package kakha.kudava.filedrivespring.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.bind.annotation.GetMapping;
import java.time.Instant;

@Getter
@Setter
public class UserDTO {
    private Long id;
    private String username;
    private String password;
    private String role;
    private boolean passwordChangeRequired;
    private boolean suspended;
    private Instant suspendedUntil;
    private boolean deleted;
    private Instant deletedAt;
    private Instant createdAt;
    private boolean totpEnabled;
    private boolean webauthnEnabled;
}
