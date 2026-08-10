package kakha.kudava.filedrivespring.dto;

import java.util.List;

public record LockboxPrivateMetadataListResponse(
        List<LockboxPrivateMetadataResponse> files
) {
    public LockboxPrivateMetadataListResponse {
        files = List.copyOf(files);
    }
}