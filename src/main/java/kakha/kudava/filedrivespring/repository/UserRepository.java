package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    @Query("SELECT u.username FROM User u")
    List<String> getUsernames();

    @Query("SELECT e FROM User e")
    List<User> findAll();

    @Query("SELECT u.permissions from User u WHERE u.username=:username")
    List<String> getUserPermissions(@Param("username") String username);

}
