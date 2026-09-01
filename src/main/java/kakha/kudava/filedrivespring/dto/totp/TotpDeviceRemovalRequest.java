package kakha.kudava.filedrivespring.dto.totp;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TotpDeviceRemovalRequest(
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
        Long authorizingDeviceId,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String code) {
    @Override
    public String toString() { return "TotpDeviceRemovalRequest[redacted]"; }
}
