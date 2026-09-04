package kakha.kudava.filedrivespring.services.jwt;

import kakha.kudava.filedrivespring.dto.LoginResponse;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** Internal result only; controllers return login and set refresh in an HttpOnly cookie. */
public record AuthenticatedSession(LoginResponse login, @JsonIgnore String refreshToken) {
    @Override public String toString() { return "AuthenticatedSession[redacted]"; }
}
