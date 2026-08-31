package kakha.kudava.filedrivespring.dto.totp;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TotpEnrollmentRequest(
        String displayName,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String secretBase32,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
        Long existingDeviceId,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String existingCode) {
    @Override
    public String toString() { return "TotpEnrollmentRequest[redacted]"; }
}
