package kakha.kudava.sftpspring.controller.view;

import jakarta.servlet.http.HttpSession;
import kakha.kudava.sftpspring.model.User;
import kakha.kudava.sftpspring.repository.UserRepository;
import kakha.kudava.sftpspring.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;

@Controller
public class UserController {

    private final UserRepository userRepository;
    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Autowired
    private UserService userService;

/*    @GetMapping("/users")
    public List<User> listAll() {
        return userRepository.findAll();
    }*/

/*    @PostMapping("/auth")
    public String auth(@RequestParam String username,
                        @RequestParam String password,
                        HttpSession session,
                        Model model) {
*//*        if(session.getAttribute("USERNAME") != null){
            model.addAttribute("message", "User: " +
                    session.getAttribute("USERNAME"));
            return "server";
        } else {*//*
            boolean auth = userService.authenticate(username, password);
            if (auth) {
                model.addAttribute("message", "Invalid username or password.");
                session.setAttribute("USERNAME", username);
                return "server";
            } else {
                model.addAttribute("message", "Invalid username or password.");
                return "login";
            }

        //}

    }*/


    @GetMapping("/register")
    public String register(Model model) {
        return "register";
    }

    @PostMapping("/register")
    public String addUser(@RequestParam String username,
                        @RequestParam String password,
                        @RequestParam String retype,
                        Model model) {
        User user = new User();

        System.out.println("username: " + username + " password: " + password + " retype: " + retype);

        if (username.length() >= 6){
            String specialChars = "!@#%&_-=;:',.<>?/`~+-";
            if (password.length() >= 8){
                if(password.matches(".*[!@#%&_=;:',.<>?/`~+-].*")){
                    if(retype.equals(password)){
                        model.addAttribute("message",
                                "All good!");
                        user.setUsername(username);
                        user.setPassword(password);
                        user.setPermissions("Read,Write");
                        userRepository.save(user);
                        model.addAttribute("message",
                                "User successfully created!");
                        return "register";
                    }
                    else {
                        model.addAttribute("message",
                                "Passwords do not match!");
                        return "register";
                    }
                }
                else {
                    model.addAttribute("message",
                            "Password requires a special character!");
                    return "register";
                }
            }
            else {
                model.addAttribute("message",
                        "Password should be at least 8 characters!");
                return "register";
            }

        } else {
            model.addAttribute("message", "Username must be at least 6 characters!");
            return "register";
        }

    }

    @GetMapping("/server/users-list")
    public String showUserList(HttpSession session,
                               Model model) {

        if(session.getAttribute("ROLE") != null) {
            if (session.getAttribute("ROLE").toString().contains("admin"))
                return "users-list";
            else {
                model.addAttribute("message", "You are not an admin!");
            }
        }
        else {
            return "redirect:/login";
        }
        return "users-list";
    }


}
