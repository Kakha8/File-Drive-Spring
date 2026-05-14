package kakha.kudava.filedrivespring.services.objects;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import jakarta.transaction.Transactional;
import kakha.kudava.filedrivespring.dto.FileItemDTO;
import kakha.kudava.filedrivespring.dto.FolderCreateRequest;
import kakha.kudava.filedrivespring.dto.FolderDTO;
import kakha.kudava.filedrivespring.dto.FolderItemDTO;
import kakha.kudava.filedrivespring.model.FileMetaData;
import kakha.kudava.filedrivespring.model.Folders;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.FileMetaDataRepository;
import kakha.kudava.filedrivespring.repository.FolderRepository;
import kakha.kudava.filedrivespring.repository.UserRepository;
import kakha.kudava.filedrivespring.services.ObjectStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Slf4j
@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final ObjectStorageService objectStorageService;
    private final UserRepository userRepository;
    private final RootFolderService rootFolderService;

    @Value("${s3.bucket}")
    private String bucket;
    private final MinioClient minioClient;
    private final FileMetaDataRepository fileMetaDataRepository;
    public FolderService(FolderRepository folderRepository, ObjectStorageService objectStorageService, UserRepository userRepository, RootFolderService rootFolderService,
                         MinioClient minioClient,
                         FileMetaDataRepository fileMetaDataRepository) {
        this.folderRepository = folderRepository;
        this.objectStorageService = objectStorageService;
        this.userRepository = userRepository;
        this.rootFolderService = rootFolderService;
        this.minioClient = minioClient;
        this.fileMetaDataRepository = fileMetaDataRepository;
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("Not authenticated");
        }

        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found: " + auth.getName()));
    }

    public FolderDTO create(FolderCreateRequest req) throws Exception {
        User user = currentUser();

        Folders parent;

        if (req.getParentId() == null) {
            parent = rootFolderService.ensureRootFolder(user);
        } else {
            parent = folderRepository.findByIdAndOwnerAndDeletedFalse(req.getParentId(), user)
                    .orElseThrow(() -> new RuntimeException("Parent folder not found or not owned by user"));
        }

        String cleanName = req.getName().replaceAll("^/|/$", "");
        String parentPrefix = parent.getPrefix();
        if (!parentPrefix.endsWith("/")) parentPrefix += "/";

        String fullPrefix = parentPrefix + cleanName + "/";

        Folders entity = new Folders();
        entity.setName(cleanName);
        entity.setPrefix(fullPrefix);
        entity.setParent(parent);
        entity.setOwner(user);
        entity.setDeleted(false);

        Folders saved = folderRepository.save(entity);

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(fullPrefix)
                        .stream(new ByteArrayInputStream(new byte[]{}), 0, -1)
                        .contentType("application/x-directory")
                        .build()
        );

        return toDto(saved);
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

    public List<FolderItemDTO> viewFolders(Long id){
        List<Folders> folders = folderRepository.findFoldersByParent_Id(id);

        List<FolderItemDTO> folderDtos = folders.stream().map(f -> {
            FolderItemDTO dto = new FolderItemDTO();
            dto.setId(f.getId());
            dto.setName(f.getName());
            dto.setPrefix(f.getPrefix());
            return dto;
        }).toList();

        return folderDtos;
    }

    public List<FileItemDTO> viewFiles(Long id){
        List<FileMetaData> files = fileMetaDataRepository.findByParent_Id(id);

        List<FileItemDTO> resultFiles = files.stream().map(file -> {
            FileItemDTO dto = new FileItemDTO();
            dto.setId(file.getId());
            dto.setFileName(file.getFileName());
            dto.setObjectKey(file.getObjectKey());
            dto.setObjectType(file.getObjectType());
            dto.setSize(file.getSize());
            dto.setDeleted(file.isDeleted());
            dto.setParentId(file.getParent() != null ? file.getParent().getId() : null);
            return dto;
        }).toList();

        return resultFiles;
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
