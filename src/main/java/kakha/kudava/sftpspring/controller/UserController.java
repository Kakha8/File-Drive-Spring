package kakha.kudava.sftpspring.controller;

import kakha.kudava.sftpspring.model.User;
import kakha.kudava.sftpspring.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;

import java.util.List;

@Controller
public class UserController {

    private final UserRepository userRepository;
    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


/*    @GetMapping("/users")
    public List<User> listAll() {
        return userRepository.findAll();
    }*/

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        Model model) {

        var userOpt = userRepository.findByUsername(username);

        if(userOpt.isPresent()) {
            var user = userOpt.get();
            if (user.getPassword().equals(password)) {
                return "redirect:/"; // view name or template (server.html)
            }
        }
        model.addAttribute("message", "Invalid username or password.");
        return "main";

    }
}
