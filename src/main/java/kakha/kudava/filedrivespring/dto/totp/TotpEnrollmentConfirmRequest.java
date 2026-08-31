package kakha.kudava.filedrivespring.dto.totp;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TotpEnrollmentConfirmRequest(
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String code) {
    @Override
    public String toString() { return "TotpEnrollmentConfirmRequest[redacted]"; }
}
