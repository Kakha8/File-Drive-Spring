package kakha.kudava.filedrivespring.services;

import kakha.kudava.filedrivespring.dto.*;
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

import java.util.*;

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

    @Transactional
    public void deletePermanently(TrashcanActionRequest request) {
        User user = currentUser();

        List<Long> fileIds = safeIds(request.getFileIds());
        List<Long> folderIds = safeIds(request.getFolderIds());

        deleteFilesPermanently(fileIds, user);
        deleteFoldersPermanently(folderIds, user);
    }

    private void deleteFilesPermanently(List<Long> fileIds, User user) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }

        List<FileMetaData> filesToDelete = new ArrayList<>();

        for (Long fileId : fileIds) {
            FileMetaData file = fileMetaDataRepository
                    .findByIdAndDeletedTrueAndParent_Owner(fileId, user)
                    .orElseThrow(() -> new RuntimeException("Trashed file not found or access denied: " + fileId));

            if (file.getObjectKey() == null || file.getObjectKey().isBlank()) {
                throw new RuntimeException("Trashed file has no trash object key: " + fileId);
            }

            filesToDelete.add(file);
        }

        for (FileMetaData file : filesToDelete) {
            objectStorageService.deleteTrashObject(file.getObjectKey());
        }

        fileMetaDataRepository.deleteAll(filesToDelete);
    }

    private void deleteFoldersPermanently(List<Long> folderIds, User user) {
        if (folderIds == null || folderIds.isEmpty()) {
            return;
        }

        List<FileMetaData> allFilesToDelete = new ArrayList<>();
        List<Folders> allFoldersToDelete = new ArrayList<>();
        List<String> trashPrefixesToDelete = new ArrayList<>();

        for (Long folderId : folderIds) {
            Folders folder = folderRepository
                    .findByIdAndDeletedTrueAndOwner(folderId, user)
                    .orElseThrow(() -> new RuntimeException("Trashed folder not found or access denied: " + folderId));

            String originalPrefix = normalizePrefix(folder.getPrefix());
            String trashPrefix = "users/" + user.getId() + "/folders/" + folder.getId() + "/";

            List<FileMetaData> filesInFolder = fileMetaDataRepository
                    .findByParent_OwnerAndDeletedTrueAndOriginalObjectKeyStartingWith(user, originalPrefix);

            List<Folders> foldersInSubtree = folderRepository
                    .findByOwnerAndDeletedTrueAndPrefixStartingWith(user, originalPrefix);

            allFilesToDelete.addAll(filesInFolder);
            allFoldersToDelete.addAll(foldersInSubtree);
            trashPrefixesToDelete.add(trashPrefix);
        }

        for (String trashPrefix : trashPrefixesToDelete) {
            objectStorageService.deleteTrashPrefix(trashPrefix);
        }

        if (!allFilesToDelete.isEmpty()) {
            fileMetaDataRepository.deleteAll(allFilesToDelete);
        }

        if (!allFoldersToDelete.isEmpty()) {
            allFoldersToDelete.sort(
                    Comparator.comparingInt((Folders folder) -> normalizePrefix(folder.getPrefix()).length())
                            .reversed()
            );

            folderRepository.deleteAll(allFoldersToDelete);
        }
    }

    private List<Long> safeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        Set<Long> uniqueIds = new LinkedHashSet<>();

        for (Long id : ids) {
            if (id != null) {
                uniqueIds.add(id);
            }
        }

        return new ArrayList<>(uniqueIds);
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }

        String normalized = prefix.trim().replace("\\", "/");

        if (!normalized.endsWith("/")) {
            normalized += "/";
        }

        return normalized;
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Not authenticated");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }
}