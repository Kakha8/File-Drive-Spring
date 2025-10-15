package kakha.kudava.sftpspring.repository;

import kakha.kudava.sftpspring.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

}
