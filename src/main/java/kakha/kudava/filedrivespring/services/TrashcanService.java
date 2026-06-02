package kakha.kudava.filedrivespring.services;

import kakha.kudava.filedrivespring.dto.TrashFileDTO;
import kakha.kudava.filedrivespring.dto.TrashFolderDTO;
import kakha.kudava.filedrivespring.dto.ViewTrashcanDTO;
import kakha.kudava.filedrivespring.model.FileMetaData;
import kakha.kudava.filedrivespring.model.Folders;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.FileMetaDataRepository;
import kakha.kudava.filedrivespring.repository.FolderRepository;
import kakha.kudava.filedrivespring.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TrashcanService {

    private final FileMetaDataRepository fileMetaDataRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;

    public TrashcanService(
            FileMetaDataRepository fileMetaDataRepository,
            FolderRepository folderRepository,
            UserRepository userRepository
    ) {
        this.fileMetaDataRepository = fileMetaDataRepository;
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
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
}