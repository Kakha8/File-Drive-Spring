package kakha.kudava.sftpspring.controller;


import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import jakarta.servlet.http.HttpSession;
import kakha.kudava.sftpspring.sftp.SftpClientService;
import kakha.kudava.sftpspring.sftp.SftpServerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    private final SftpServerService sftp;
    private final SftpClientService client;

    public HomeController(SftpServerService sftp, SftpClientService client) {
        this.sftp = sftp;
        this.client = client;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("sftpRunning", sftp.isRunning());
        return "server";
    }

    @PostMapping("/start-server")
    public String startServer(@RequestParam("username") String username,
                              @RequestParam("password") String password,
                              Model model) {
        try {
            sftp.start(username, password);
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

    @GetMapping("/client")
    public String client(Model model) {
        return "client";
    }

    @PostMapping("/connect")
    public String upload(@RequestParam("username") String username,
                         @RequestParam("password") String password,
                         Model model, HttpSession session) throws JSchException, SftpException {
        ChannelSftp con = null;
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
            // also put into model for immediate render
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
        return "user-con";
    }

    @PostMapping("/disconnect")
    public String disconnect(HttpSession session, Model model) throws JSchException {
        String username = (String) session.getAttribute("sftpUser");
        client.disconnectSFTP(username);
        model.addAttribute("message", "Client" + username +
                " disconnected.");
        session.invalidate();
        return "client";
    }

    @GetMapping("/user")
    public String userPage() {
        // user.html uses session.sftpConnected / session.sftpUser directly
        return "user-con";
    }

    @GetMapping("/main")
    public String mainPage() {
        return "main";
    }
}
