package kakha.kudava.filedrivespring.services;

import jakarta.transaction.Transactional;
import kakha.kudava.filedrivespring.dto.UserDTO;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserDTO saveUser(UserDTO userDTO) {
        User user = new User();
        user.setUsername(userDTO.getUsername());
        user.setPassword(userDTO.getPassword());
        userRepository.save(user);
        log.info("User saved successfully: {}", userDTO.getUsername());
        return userDTO;
    }

    public List<UserDTO> getAllUsers() {
        List<User> users = userRepository.findAll();
        List<UserDTO> userDTOs = new ArrayList<>();
        for (User user : users) {
            UserDTO userDTO = new UserDTO();
            userDTO.setId(user.getId());
            userDTO.setUsername(user.getUsername());

            userDTOs.add(userDTO);
        }
        log.info("Retuning all users from database...");
        return userDTOs;
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByEmail(String userName) {
        return userRepository.findByUsername(userName);
    }

    @Transactional
    public void delete(Long id) {
        userRepository.deleteById(id);
        log.info("Object deleted successfully {}", id);
    }

    public List<User> getUsers() {
        return userRepository.findAll();
    }
}
