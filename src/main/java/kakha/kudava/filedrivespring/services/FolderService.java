package kakha.kudava.filedrivespring.services;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import jakarta.transaction.Transactional;
import kakha.kudava.filedrivespring.dto.FolderCreateRequest;
import kakha.kudava.filedrivespring.dto.FolderDTO;
import kakha.kudava.filedrivespring.model.FileMetaData;
import kakha.kudava.filedrivespring.model.Folders;
import kakha.kudava.filedrivespring.repository.FileMetaDataRepository;
import kakha.kudava.filedrivespring.repository.FolderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final ObjectStorageService objectStorageService;

    @Value("${s3.bucket}")
    private String bucket;
    private final MinioClient minioClient;
    private final FileMetaDataRepository fileMetaDataRepository;
    public FolderService(FolderRepository folderRepository, ObjectStorageService objectStorageService,
                         MinioClient minioClient,
                         FileMetaDataRepository fileMetaDataRepository) {
        this.folderRepository = folderRepository;
        this.objectStorageService = objectStorageService;
        this.minioClient = minioClient;
        this.fileMetaDataRepository = fileMetaDataRepository;
    }

    public FolderDTO create(FolderCreateRequest req)
            throws ServerException,
            InsufficientDataException,
            ErrorResponseException,
            IOException,
            NoSuchAlgorithmException,
            InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {

        String fullPrefix = buildFullPrefix(req.getName(), req.getParentId());

        Folders entity = new Folders();
        entity.setName(req.getName().replaceAll("^/|/$", ""));
        entity.setPrefix(fullPrefix);

        if (req.getParentId() != null) {
            Folders parent = folderRepository.findById(req.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent folder not found: " + req.getParentId()));
            entity.setParent(parent);
        }

        Folders saved = folderRepository.save(entity);

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(fullPrefix)
                        .stream(
                                new ByteArrayInputStream(new byte[]{}),
                                0,
                                -1
                        )
                        .build()
        );

        return toDto(entity);
    }

    private String buildFullPrefix(String folderName, Long parentId) {
        String normalizedName = folderName.replaceAll("^/|/$", "");

        if (parentId == null) {
            return normalizedName + "/";
        }

        Folders parent = folderRepository.findById(parentId)
                .orElseThrow(() -> new RuntimeException("Parent folder not found: " + parentId));

        String parentPrefix = parent.getPrefix();
        if (!parentPrefix.endsWith("/")) parentPrefix += "/";

        return parentPrefix + normalizedName + "/";
    }

    @Transactional
    public void delete(Long id) throws
            InsufficientDataException,
            ErrorResponseException,
            IOException, NoSuchAlgorithmException, InvalidKeyException, InstantiationException, IllegalAccessException {
        String prefix = folderRepository.findPrefixById(id);
        if (prefix == null) {
            throw new RuntimeException("Folder not found: " + id);
        }

        // normalizing
        String p = prefix.trim().replace("\\", "/");
        if (!p.endsWith("/")) {
            p = p + "/";
        }

        objectStorageService.deleteByPrefix(p);

        int filesDeleted = fileMetaDataRepository.softDeleteFilesByFolderPrefix(p);
        int foldersDeleted = folderRepository.softDeleteTreeByPrefix(p);

        log.info("Soft-deleted {} files and {} folders for prefix={}", filesDeleted, foldersDeleted, p);
    }
    private FolderDTO toDto(Folders f) {
        FolderDTO dto = new FolderDTO();
        dto.setId(f.getId());
        dto.setName(f.getName());
        dto.setPrefix(f.getPrefix());
        dto.setParentId(f.getParent() != null ? f.getParent().getId() : null);
        return dto;
    }

}
