package kakha.kudava.filedrivespring.controller.view;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import jakarta.servlet.http.HttpSession;
import kakha.kudava.filedrivespring.services.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/client")
public class ClientController {

    private final SftpServerService sftp;

    @Autowired
    private UserService userService;

    @Autowired
    private UserSessionService userSessionService;

    @Autowired
    private SftpSessionRegistry sftpSessionRegistry;

    public ClientController(SftpServerService sftp, UserService userService) {
        this.sftp = sftp;
        this.userService = userService;
    }

    @GetMapping("/client-home")
    public String client(HttpSession session, Model model) {

        String username = (String) session.getAttribute("USERNAME");
        if (userSessionService.checkUserSession(session)) {
            if (sftpSessionRegistry.isUserConnected(username)) {
                model.addAttribute("status", "Connected as " + username);
                return "client";
            } else
                return "client";
        }
        else
            return "redirect:/login";
    }


}
