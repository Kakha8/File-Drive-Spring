package kakha.kudava.filedrivespring.services;

import kakha.kudava.filedrivespring.dto.FileMetaDataDTO;
import kakha.kudava.filedrivespring.model.FileMetaData;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {

    private final ObjectStorageService objectStorageService;

    public FileService(ObjectStorageService objectStorageService) {
        this.objectStorageService = objectStorageService;
    }

    public FileMetaDataDTO upload(MultipartFile file, Long parentId) throws Exception {
        FileMetaData entity = objectStorageService.upload(file, parentId);
        return mapToDto(entity);
    }

    private FileMetaDataDTO mapToDto(FileMetaData f) {
        FileMetaDataDTO dto = new FileMetaDataDTO();
        dto.setId(f.getId());
        dto.setObjectKey(f.getObjectKey());
        dto.setObjectType(f.getObjectType());
        dto.setDeleted(f.isDeleted());
        dto.setParentId(f.getParent() != null ? f.getParent().getId() : null);
        return dto;
    }
}
