package kakha.kudava.filedrivespring.services;

import jakarta.transaction.Transactional;
import kakha.kudava.filedrivespring.dto.SharedItemDTO;
import kakha.kudava.filedrivespring.enums.SharingRole;
import kakha.kudava.filedrivespring.model.FileMetaData;
import kakha.kudava.filedrivespring.model.Folders;
import kakha.kudava.filedrivespring.model.SharingPermission;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.FileMetaDataRepository;
import kakha.kudava.filedrivespring.repository.FolderRepository;
import kakha.kudava.filedrivespring.repository.SharingPermissionRepository;
import kakha.kudava.filedrivespring.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SharingService {

    private final SharingPermissionRepository sharingPermissionRepository;
    private final FileMetaDataRepository fileMetaDataRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;

    public SharingService(
            SharingPermissionRepository sharingPermissionRepository,
            FileMetaDataRepository fileMetaDataRepository,
            FolderRepository folderRepository,
            UserRepository userRepository
    ) {
        this.sharingPermissionRepository = sharingPermissionRepository;
        this.fileMetaDataRepository = fileMetaDataRepository;
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("Not authenticated");
        }

        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found: " + auth.getName()));
    }

    @Transactional
    public List<SharedItemDTO> shareFiles(List<Long> fileIds, String targetUsername, SharingRole role) {
        User owner = currentUser();

        if (fileIds == null || fileIds.isEmpty()) {
            throw new RuntimeException("No files selected");
        }

        User sharedWith = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("User not found: " + targetUsername));

        validateNotSharingWithSelf(owner, sharedWith);

        List<SharedItemDTO> sharedItems = new ArrayList<>();

        for (Long fileId : fileIds) {
            FileMetaData file = requireOwnedFile(fileId, owner);

            SharingPermission permission = upsertFileShare(file, owner, sharedWith, normalizeRole(role));
            sharedItems.add(toDto(permission));
        }

        return sharedItems;
    }

    @Transactional
    public List<SharedItemDTO> shareFolders(List<Long> folderIds, String targetUsername, SharingRole role) {
        User owner = currentUser();

        if (folderIds == null || folderIds.isEmpty()) {
            throw new RuntimeException("No folders selected");
        }

        User sharedWith = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("User not found: " + targetUsername));

        validateNotSharingWithSelf(owner, sharedWith);

        List<SharedItemDTO> sharedItems = new ArrayList<>();

        for (Long folderId : folderIds) {
            Folders folder = requireOwnedFolder(folderId, owner);

            if (folder.getParent() == null) {
                throw new RuntimeException("Sharing the root folder is not allowed. Invalid folder id: " + folderId);
            }

            SharingPermission permission = upsertFolderShare(folder, owner, sharedWith, normalizeRole(role));
            sharedItems.add(toDto(permission));
        }

        return sharedItems;
    }

    @Transactional
    public void revokeShare(Long shareId) {
        User owner = currentUser();

        SharingPermission permission = sharingPermissionRepository.findById(shareId)
                .orElseThrow(() -> new RuntimeException("Share not found"));

        if (!permission.isActive()) {
            throw new RuntimeException("Share not found");
        }

        if (permission.getFile() != null) {
            if (!ownsFile(permission.getFile(), owner)) {
                throw new RuntimeException("Share not found");
            }
        } else if (permission.getFolder() != null) {
            if (!ownsFolder(permission.getFolder(), owner)) {
                throw new RuntimeException("Share not found");
            }
        } else {
            throw new RuntimeException("Invalid share");
        }

        permission.setActive(false);
        permission.setRevokedAt(Instant.now());
        permission.setUpdatedAt(Instant.now());

        sharingPermissionRepository.save(permission);
    }

    public List<SharedItemDTO> getSharedWithMe() {
        User user = currentUser();

        return sharingPermissionRepository.findBySharedWithAndActiveTrue(user)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<SharedItemDTO> getSharedByMe() {
        User owner = currentUser();

        return sharingPermissionRepository.findByOwnerAndActiveTrue(owner)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<SharedItemDTO> getFileShares(Long fileId) {
        User owner = currentUser();
        FileMetaData file = requireOwnedFile(fileId, owner);

        return sharingPermissionRepository.findByFileAndActiveTrue(file)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<SharedItemDTO> getFolderShares(Long folderId) {
        User owner = currentUser();
        Folders folder = requireOwnedFolder(folderId, owner);

        return sharingPermissionRepository.findByFolderAndActiveTrue(folder)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public boolean canViewFile(FileMetaData file, User user) {
        if (ownsFile(file, user)) {
            return true;
        }

        if (hasDirectFileRole(file, user, SharingRole.VIEWER)) {
            return true;
        }

        return file.getParent() != null && canAccessFolder(file.getParent(), user, SharingRole.VIEWER);
    }

    public boolean canEditFile(FileMetaData file, User user) {
        if (ownsFile(file, user)) {
            return true;
        }

        if (hasDirectFileRole(file, user, SharingRole.EDITOR)) {
            return true;
        }

        return file.getParent() != null && canAccessFolder(file.getParent(), user, SharingRole.EDITOR);
    }

    public boolean canViewFolder(Folders folder, User user) {
        return canAccessFolder(folder, user, SharingRole.VIEWER);
    }

    public boolean canEditFolder(Folders folder, User user) {
        return canAccessFolder(folder, user, SharingRole.EDITOR);
    }

    public boolean showsSharedIndicator(FileMetaData file, User viewer) {
        if (file == null || viewer == null) {
            return false;
        }

        if (!ownsFile(file, viewer)) {
            return true;
        }

        if (sharingPermissionRepository.existsByFileAndActiveTrue(file)) {
            return true;
        }

        return file.getParent() != null && hasActiveFolderShareInChain(file.getParent());
    }

    public boolean showsSharedIndicator(Folders folder, User viewer) {
        if (folder == null || viewer == null) {
            return false;
        }

        if (!ownsFolder(folder, viewer)) {
            return true;
        }

        return hasActiveFolderShareInChain(folder);
    }

    private FileMetaData requireOwnedFile(Long fileId, User owner) {
        FileMetaData file = fileMetaDataRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!ownsFile(file, owner)) {
            throw new RuntimeException("File not found");
        }

        return file;
    }

    private Folders requireOwnedFolder(Long folderId, User owner) {
        Folders folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        if (!ownsFolder(folder, owner)) {
            throw new RuntimeException("Folder not found");
        }

        return folder;
    }

    private boolean hasActiveFolderShareInChain(Folders folder) {
        Folders current = folder;

        while (current != null) {
            if (sharingPermissionRepository.existsByFolderAndActiveTrue(current)) {
                return true;
            }

            current = current.getParent();
        }

        return false;
    }

    private boolean canAccessFolder(Folders folder, User user, SharingRole requiredRole) {
        Folders current = folder;

        while (current != null) {
            if (ownsFolder(current, user)) {
                return true;
            }

            Optional<SharingPermission> permission =
                    sharingPermissionRepository.findByFolderAndSharedWithAndActiveTrue(current, user);

            if (permission.isPresent() && roleIncludes(permission.get().getRole(), requiredRole)) {
                return true;
            }

            current = current.getParent();
        }

        return false;
    }

    private boolean hasDirectFileRole(FileMetaData file, User user, SharingRole requiredRole) {
        return sharingPermissionRepository.findByFileAndSharedWithAndActiveTrue(file, user)
                .map(permission -> roleIncludes(permission.getRole(), requiredRole))
                .orElse(false);
    }

    private SharingPermission upsertFileShare(
            FileMetaData file,
            User owner,
            User sharedWith,
            SharingRole role
    ) {
        SharingPermission permission = sharingPermissionRepository
                .findByFileAndSharedWith(file, sharedWith)
                .orElseGet(SharingPermission::new);

        permission.setFile(file);
        permission.setFolder(null);
        permission.setOwner(owner);
        permission.setSharedBy(owner);
        permission.setSharedWith(sharedWith);
        permission.setRole(role);
        permission.setActive(true);
        permission.setRevokedAt(null);
        permission.setUpdatedAt(Instant.now());

        return sharingPermissionRepository.save(permission);
    }

    private SharingPermission upsertFolderShare(
            Folders folder,
            User owner,
            User sharedWith,
            SharingRole role
    ) {
        SharingPermission permission = sharingPermissionRepository
                .findByFolderAndSharedWith(folder, sharedWith)
                .orElseGet(SharingPermission::new);

        permission.setFolder(folder);
        permission.setFile(null);
        permission.setOwner(owner);
        permission.setSharedBy(owner);
        permission.setSharedWith(sharedWith);
        permission.setRole(role);
        permission.setActive(true);
        permission.setRevokedAt(null);
        permission.setUpdatedAt(Instant.now());

        return sharingPermissionRepository.save(permission);
    }

    private boolean ownsFile(FileMetaData file, User user) {
        return file != null
                && file.getOwner() != null
                && sameUser(file.getOwner(), user);
    }

    private boolean ownsFolder(Folders folder, User user) {
        return folder != null
                && folder.getOwner() != null
                && sameUser(folder.getOwner(), user);
    }

    private boolean sameUser(User a, User b) {
        return a != null
                && b != null
                && a.getId() != null
                && a.getId().equals(b.getId());
    }

    private void validateNotSharingWithSelf(User owner, User sharedWith) {
        if (sameUser(owner, sharedWith)) {
            throw new RuntimeException("You cannot share a resource with yourself");
        }
    }

    private SharingRole normalizeRole(SharingRole role) {
        return role == null ? SharingRole.VIEWER : role;
    }

    private boolean roleIncludes(SharingRole actualRole, SharingRole requiredRole) {
        if (actualRole == SharingRole.EDITOR) {
            return true;
        }

        return actualRole == SharingRole.VIEWER && requiredRole == SharingRole.VIEWER;
    }

    private SharedItemDTO toDto(SharingPermission permission) {
        SharedItemDTO dto = new SharedItemDTO();

        dto.setShareId(permission.getId());
        dto.setOwnerUsername(permission.getOwner().getUsername());
        dto.setSharedWithUsername(permission.getSharedWith().getUsername());
        dto.setRole(permission.getRole());

        if (permission.getFile() != null) {
            dto.setResourceType("FILE");
            dto.setResourceId(permission.getFile().getId());
            dto.setName(permission.getFile().getFileName());
            dto.setSize(permission.getFile().getSize());
        } else if (permission.getFolder() != null) {
            dto.setResourceType("FOLDER");
            dto.setResourceId(permission.getFolder().getId());
            dto.setName(permission.getFolder().getName());
            dto.setSize(null);
        }

        return dto;
    }
}