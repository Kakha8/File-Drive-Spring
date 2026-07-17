package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.Favorites;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoritesRepository extends JpaRepository<Favorites, Integer> {
}
