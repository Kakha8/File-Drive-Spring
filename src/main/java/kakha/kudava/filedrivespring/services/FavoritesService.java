package kakha.kudava.filedrivespring.services;

import jakarta.transaction.Transactional;
import kakha.kudava.filedrivespring.dto.FavoritesRequestDTO;
import kakha.kudava.filedrivespring.enums.EntityType;
import kakha.kudava.filedrivespring.model.Favorites;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.FavoritesRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
public class FavoritesService {

    private final FavoritesRepository favoritesRepository;
    private final ResourceAccessService resourceAccessService;

    public FavoritesService(
            FavoritesRepository favoritesRepository,
            ResourceAccessService resourceAccessService
    ) {
        this.favoritesRepository = favoritesRepository;
        this.resourceAccessService = resourceAccessService;
    }

    @Transactional
    public void add(FavoritesRequestDTO request) {
        Objects.requireNonNull(request, "Favorites request cannot be null");

        User currentUser = resourceAccessService.currentUser();

        for (Long fileId : safeIds(request.getFileIds())) {
            resourceAccessService.requireFileView(fileId);

            addOrRestore(
                    currentUser,
                    EntityType.FILE,
                    fileId
            );
        }

        for (Long folderId : safeIds(request.getFolderIds())) {
            resourceAccessService.requireFolderView(folderId);

            addOrRestore(
                    currentUser,
                    EntityType.FOLDER,
                    folderId
            );
        }
    }

    private void addOrRestore(
            User user,
            EntityType entityType,
            Long entityId
    ) {
        favoritesRepository
                .findByUserAndEntityTypeAndEntityId(
                        user,
                        entityType,
                        entityId
                )
                .ifPresentOrElse(
                        favorite -> {
                            if (!favorite.isActive()) {
                                favorite.restore();
                            }
                        },
                        () -> favoritesRepository.save(
                                new Favorites(user, entityType, entityId)
                        )
                );
    }

    private List<Long> safeIds(List<Long> ids) {
        if (ids == null) {
            return Collections.emptyList();
        }

        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}