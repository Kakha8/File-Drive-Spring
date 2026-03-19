package kakha.kudava.filedrivespring.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MoveFileRequest {
    private Long fileId;
    private Long targetFolderId;

    public MoveFileRequest(Long targetFolderId) {
        this.targetFolderId = targetFolderId;
    }
}
