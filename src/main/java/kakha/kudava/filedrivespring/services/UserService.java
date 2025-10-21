package kakha.kudava.filedrivespring.services;

import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
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

    public List<String> getUsernames() {
        return userRepository.getUsernames();
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

    public List<User> getUsers() {
        return userRepository.findAll();
    }
}
