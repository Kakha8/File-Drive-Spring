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
    private final SftpClientService client;

    @Autowired
    private UserService userService;

    @Autowired
    private UserSessionService userSessionService;

    @Autowired
    private SftpSessionRegistry sftpSessionRegistry;

    public ClientController(SftpServerService sftp, SftpClientService client, UserService userService) {
        this.sftp = sftp;
        this.client = client;
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


    @PostMapping("/connect")
    public String upload(@RequestParam("password") String password,
                         Model model, HttpSession session) throws JSchException, SftpException {
        ChannelSftp con = null;
        String username = userSessionService.getUsernameFromSession(session);
        try {
            con = client.connectSFTP(username, password);
            client.showDir(con);

            boolean checkCon = client.isConnected(con);
            if (checkCon) {
                session.setAttribute("sftpConnected", true);
                session.setAttribute("sftpUser", username);
                System.out.println("connected as " + username);
            } else {
                session.setAttribute("sftpConnected", false);
                session.removeAttribute("sftpUser");
            }

            System.out.println(client.showDir(con));

            model.addAttribute("sftpConnected", checkCon);
            model.addAttribute("connectedUser", checkCon ? username : null);

        } finally {
            if (con != null) {
                try {
                    var sess = con.getSession();
                    if (con.isConnected()) con.disconnect();
                    if (sess != null && sess.isConnected()) sess.disconnect();
                } catch (JSchException ignore) {
                }
            }
        }
        return "redirect:/client/client-home";
    }

    @PostMapping("/disconnect")
    public String disconnect(HttpSession session, Model model) throws JSchException {
        String username = (String) session.getAttribute("sftpUser");
        client.disconnectSFTP(username);
        model.addAttribute("message", "Client" + username +
                " disconnected.");
        session.invalidate();
        return "redirect:/client/client-home";
    }

}
