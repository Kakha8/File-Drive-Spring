package kakha.kudava.sftpspring.controller;

import jakarta.servlet.http.HttpSession;
import kakha.kudava.sftpspring.model.User;
import kakha.kudava.sftpspring.services.SftpClientService;
import kakha.kudava.sftpspring.services.SftpServerService;
import kakha.kudava.sftpspring.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class ServerController {
    private final SftpServerService sftp;
    private final SftpClientService client;

    @Autowired
    private UserService userService;

    public ServerController(SftpServerService sftp, SftpClientService client) {
        this.sftp = sftp;
        this.client = client;
    }

    @GetMapping("/server")
    public String server(HttpSession session, Model model) {
        Object user = session.getAttribute("USERNAME");
        if (user == null) return "redirect:/login";

        boolean running = sftp != null && sftp.isRunning();
        model.addAttribute("sftpRunning", running);
        model.addAttribute("username", user.toString());
        return "server";
    }

    @PostMapping("/start-server")
    public String startServer(@RequestParam("password") String password,
                              HttpSession session, Model model) {
        try {
            String usr = session.getAttribute("USERNAME").toString();
            List<User> users = userService.getUsers();
            sftp.addDefaultUsers(users);
            sftp.start();
            model.addAttribute("message", "SFTP server started.");
        } catch (Exception e) {
            model.addAttribute("message", "Failed to start SFTP: " + e.getMessage());
        }
        model.addAttribute("sftpRunning", sftp.isRunning());
        return "server";
    }

    @PostMapping("/stop-server")
    public String stopServer(Model model) {
        sftp.stop(true);
        model.addAttribute("message", "SFTP server stopped.");
        model.addAttribute("sftpRunning", sftp.isRunning());
        return "server";
    }

    @GetMapping("/userstemp")
    public String tempusers(HttpSession session, Model model) {
        return "userstemp";
    }
}
