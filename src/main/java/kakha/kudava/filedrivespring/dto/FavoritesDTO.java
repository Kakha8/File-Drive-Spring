package kakha.kudava.filedrivespring.dto;

import kakha.kudava.filedrivespring.enums.EntityType;
import kakha.kudava.filedrivespring.model.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class FavoritesDTO {
    private Long id;
    private User user;
    private EntityType entityType;
    private Long entityId;
    private Instant createdAt;
    private Instant removedAt;
}
