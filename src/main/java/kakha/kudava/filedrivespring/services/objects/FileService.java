package kakha.kudava.filedrivespring.services.objects;

import kakha.kudava.filedrivespring.dto.FileMetaDataDTO;
import kakha.kudava.filedrivespring.exceptions.UploadCanceledException;
import kakha.kudava.filedrivespring.model.FileMetaData;
import kakha.kudava.filedrivespring.repository.FileMetaDataRepository;
import kakha.kudava.filedrivespring.services.ObjectStorageService;
import kakha.kudava.filedrivespring.services.UploadCancellationService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


@Service
public class FileService {

    private final ObjectStorageService objectStorageService;
    private final FileMetaDataRepository fileMetaDataRepository;
    private final UploadCancellationService uploadCancellationService;

    public FileService(ObjectStorageService objectStorageService, FileMetaDataRepository fileMetaDataRepository, UploadCancellationService uploadCancellationService) {
        this.objectStorageService = objectStorageService;
        this.fileMetaDataRepository = fileMetaDataRepository;
        this.uploadCancellationService = uploadCancellationService;
    }

    public FileMetaDataDTO upload(MultipartFile file, Long parentId, String uploadId) throws Exception {
        try {
            throwIfUploadCanceled(uploadId);

            FileMetaData entity = objectStorageService.upload(file, parentId, uploadId);

            throwIfUploadCanceled(uploadId);

            return mapToDto(entity);
        } finally {
            uploadCancellationService.clear(uploadId);
        }
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

    private void throwIfUploadCanceled(String uploadId) {
        if (uploadCancellationService.isCanceled(uploadId)) {
            throw new UploadCanceledException("Upload canceled");
        }
    }
}
