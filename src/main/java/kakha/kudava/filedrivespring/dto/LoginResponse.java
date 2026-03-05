package kakha.kudava.filedrivespring.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginResponse {
    private String accessToken;

    public LoginResponse(String token) {
        this.accessToken = token;
    }
}
