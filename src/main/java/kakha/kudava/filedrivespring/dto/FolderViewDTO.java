package kakha.kudava.filedrivespring.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FolderViewDTO {
    private List<FolderItemDTO> folders;
    private List<FileItemDTO> files;
}
