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
    public SharedItemDTO shareFile(Long fileId, String targetUsername, SharingRole role) {
        User owner = currentUser();

        FileMetaData file = fileMetaDataRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!ownsFile(file, owner)) {
            throw new RuntimeException("You can only share files you own");
        }

        User sharedWith = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("User not found: " + targetUsername));

        validateNotSharingWithSelf(owner, sharedWith);

        SharingPermission permission = upsertFileShare(file, owner, sharedWith, role);

        return toDto(permission);
    }

    @Transactional
    public SharedItemDTO shareFolder(Long folderId, String targetUsername, SharingRole role) {
        User owner = currentUser();

        Folders folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        if (!ownsFolder(folder, owner)) {
            throw new RuntimeException("You can only share folders you own");
        }

        if (folder.getParent() == null) {
            throw new RuntimeException("Sharing the root folder is not allowed");
        }

        User sharedWith = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("User not found: " + targetUsername));

        validateNotSharingWithSelf(owner, sharedWith);

        SharingPermission permission = upsertFolderShare(folder, owner, sharedWith, role);

        return toDto(permission);
    }

    @Transactional
    public void revokeShare(Long shareId) {
        User owner = currentUser();

        SharingPermission permission = sharingPermissionRepository.findById(shareId)
                .orElseThrow(() -> new RuntimeException("Share not found"));

        if (!sameUser(permission.getOwner(), owner)) {
            throw new RuntimeException("You can only revoke shares for resources you own");
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
        return file.getParent() != null
                && file.getParent().getOwner() != null
                && sameUser(file.getParent().getOwner(), user);
    }

    private boolean ownsFolder(Folders folder, User user) {
        return folder.getOwner() != null && sameUser(folder.getOwner(), user);
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
        } else if (permission.getFolder() != null) {
            dto.setResourceType("FOLDER");
            dto.setResourceId(permission.getFolder().getId());
            dto.setName(permission.getFolder().getName());
        }

        return dto;
    }
}