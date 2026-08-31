package kakha.kudava.filedrivespring.dto.totp;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MfaLoginRequest(
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String challengeToken,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String code) {
    @Override public String toString() { return "MfaLoginRequest[redacted]"; }
}
