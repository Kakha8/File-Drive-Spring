package kakha.kudava.filedrivespring.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class FileItemDTO {
    private Long id;
    private String fileName;
    private String objectKey;
    private String objectType;
    private Long parentId;
    private Long size;
    private boolean deleted;
}
