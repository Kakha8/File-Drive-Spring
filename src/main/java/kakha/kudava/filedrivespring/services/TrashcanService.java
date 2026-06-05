package kakha.kudava.filedrivespring.services;

import kakha.kudava.filedrivespring.dto.MoveToTrashReqDTO;
import kakha.kudava.filedrivespring.dto.TrashFileDTO;
import kakha.kudava.filedrivespring.dto.TrashFolderDTO;
import kakha.kudava.filedrivespring.dto.ViewTrashcanDTO;
import kakha.kudava.filedrivespring.model.FileMetaData;
import kakha.kudava.filedrivespring.model.Folders;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.FileMetaDataRepository;
import kakha.kudava.filedrivespring.repository.FolderRepository;
import kakha.kudava.filedrivespring.repository.UserRepository;
import kakha.kudava.filedrivespring.services.objects.FolderService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class TrashcanService {

    private final FileMetaDataRepository fileMetaDataRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final ObjectStorageService objectStorageService;
    private final FolderService folderService;

    public TrashcanService(
            FileMetaDataRepository fileMetaDataRepository,
            FolderRepository folderRepository,
            UserRepository userRepository, ObjectStorageService objectStorageService, FolderService folderService
    ) {
        this.fileMetaDataRepository = fileMetaDataRepository;
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
        this.objectStorageService = objectStorageService;
        this.folderService = folderService;
    }

    public ViewTrashcanDTO viewTrashcan() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User is not authenticated");
        }

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<TrashFileDTO> deletedFiles =
                fileMetaDataRepository.findByParent_OwnerAndDeletedTrue(user)
                        .stream()
                        .map(file -> new TrashFileDTO(
                                file.getId(),
                                file.getFileName(),
                                file.getObjectType(),
                                file.getSize(),
                                file.getCreationDate(),
                                file.getObjectKey(),
                                file.getOriginalObjectKey()
                        ))
                        .toList();

        List<TrashFolderDTO> deletedFolders =
                folderRepository.findByOwnerAndDeletedTrue(user)
                        .stream()
                        .map(folder -> new TrashFolderDTO(
                                folder.getId(),
                                folder.getName(),
                                folder.getPrefix()
                        ))
                        .toList();

        return new ViewTrashcanDTO(deletedFiles, deletedFolders);
    }

    @Transactional
    public void moveToTrash(MoveToTrashReqDTO req) throws Exception {
        if (req == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        List<Long> fileIds = req.getFileIds() == null
                ? List.of()
                : req.getFileIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<Long> folderIds = req.getFolderIds() == null
                ? List.of()
                : req.getFolderIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (fileIds.isEmpty() && folderIds.isEmpty()) {
            throw new IllegalArgumentException("No files or folders provided");
        }

        if (!fileIds.isEmpty()) {
            objectStorageService.deleteMultipleFiles(fileIds);
        }

        if (!folderIds.isEmpty()) {
            folderService.deleteMultiple(folderIds);
        }
    }
}