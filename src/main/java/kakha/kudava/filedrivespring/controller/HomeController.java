package kakha.kudava.filedrivespring.controller;


import jakarta.servlet.http.HttpSession;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.services.SftpClientService;
import kakha.kudava.filedrivespring.services.SftpServerService;
import kakha.kudava.filedrivespring.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final SftpServerService sftp;
    private final SftpClientService client;

    @Autowired
    private UserService userService;

    public HomeController(SftpServerService sftp, SftpClientService client, UserService userService) {
        this.sftp = sftp;
        this.client = client;
        this.userService = userService;
    }

    @GetMapping("/")
    public String home(HttpSession session, Model model) {
        Object user = session.getAttribute("USERNAME");
        if (user == null) return "redirect:/login";

        boolean running = sftp != null && sftp.isRunning();
        model.addAttribute("sftpRunning", running);
        model.addAttribute("username", user.toString());
        return "index";
    }




/*    @GetMapping("/client")
    public String client(Model model) {
        return "client";
    }*/
/*
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
    }*/

    @GetMapping("/user")
    public String userPage() {
        // user.html uses session.sftpConnected / session.sftpUser directly
        User user = new User();
        user.setUsername("admin");
        boolean checkAdmin = userService.isAdmin(user);
        System.out.println(checkAdmin);
        return "user-con";
    }

    @GetMapping("/yle")
    public String yle(Model model) {
        return "index";
    }
/*    @GetMapping("/main")
    public String mainPage() {
        return "login";
    }*/
}
