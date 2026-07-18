package kakha.kudava.filedrivespring.services;

import org.springframework.transaction.annotation.Transactional;
import kakha.kudava.filedrivespring.dto.FavoritesDTO;
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

    @Transactional(readOnly = true)
    public List<FavoritesDTO> getAll() {
        User currentUser = resourceAccessService.currentUser();

        return favoritesRepository
                .findAllByUserAndRemovedAtIsNullOrderByCreatedAtDesc(currentUser)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private FavoritesDTO toDto(Favorites favorite) {
        FavoritesDTO dto = new FavoritesDTO();

        dto.setId(favorite.getId());
        dto.setEntityType(favorite.getEntityType());
        dto.setEntityId(favorite.getEntityId());
        dto.setCreatedAt(favorite.getCreatedAt());

        return dto;
    }

    @Transactional
    public void remove(Long favoriteId) {
        User currentUser = resourceAccessService.currentUser();

        Favorites favorite = favoritesRepository
                .findByIdAndUser(favoriteId, currentUser)
                .orElseThrow(() ->
                        new RuntimeException("Favorite not found")
                );

        favorite.remove();
    }
}