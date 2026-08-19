package kakha.kudava.filedrivespring.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginResponse {
    private String accessToken;
    private Long userId;
    private String username;

    public LoginResponse(String token, Long userId, String username) {
        this.accessToken = token;
        this.userId = userId;
        this.username = username;
    }
}
