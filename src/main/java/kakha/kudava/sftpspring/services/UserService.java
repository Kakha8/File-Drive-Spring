package kakha.kudava.sftpspring.services;

import kakha.kudava.sftpspring.model.User;
import kakha.kudava.sftpspring.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public boolean authenticate(String username, String password) {
        Optional<User> user = userRepository.findByUsername(username);

        String dbPassword = user.get().getPassword();
        if (user != null && dbPassword.equals(password))
            return true;
        else
            return false;
    }

    public boolean isAdmin(User user){
        Optional<User> dbUser = userRepository.findByUsername(user.getUsername());
        if (dbUser == null) {
            return false;
        }

        String permissions = dbUser.get().getPermissions();
        if(permissions.contains("admin"))
            return true;
        else
            return false;
    }
}
