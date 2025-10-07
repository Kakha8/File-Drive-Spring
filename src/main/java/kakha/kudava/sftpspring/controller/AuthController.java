package kakha.kudava.sftpspring.controller;
// AuthController.java

import jakarta.servlet.http.HttpSession;
import kakha.kudava.sftpspring.services.SftpServerService;
import kakha.kudava.sftpspring.services.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserService userService;
    private final SftpServerService sftpService;

    public AuthController(UserService userService, SftpServerService sftpServerService) {
        this.userService = userService;
        this.sftpService = sftpServerService;
    }


    @GetMapping({"/login", "/main"})
    public String loginPage(HttpSession session, Model model) {
        Object username = session.getAttribute("USERNAME");

        if (username != null) {
            // No session or not logged in → redirect to login
            return "redirect:/login";
        } else
            return "login";
    }


    @PostMapping("/auth")
    public String auth(@RequestParam String username,
                       @RequestParam String password,
                       HttpSession session,
                       RedirectAttributes ra) {
        if (session.getAttribute("USERNAME") != null) {
            ra.addFlashAttribute("message", "You are already logged in as " + session.getAttribute("USERNAME"));
            return "redirect:/server";
        }

        if (userService.authenticate(username, password)) {
            session.setAttribute("USERNAME", username);
            ra.addFlashAttribute("message", "Welcome, " + username + "!");
            //System.out.println(userService.getUsernames());
            return "redirect:/server";
        } else {
            ra.addFlashAttribute("message", "Invalid username or password.");
            return "redirect:/login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes ra) {
        if (session != null) session.invalidate();
        ra.addFlashAttribute("message", "You have been logged out.");
        return "redirect:/login";
    }
}
