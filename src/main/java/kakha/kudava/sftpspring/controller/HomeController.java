package kakha.kudava.sftpspring.controller;


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
        return "index";
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
        return "index";
    }

    @PostMapping("/stop-server")
    public String stopServer(Model model) {
        sftp.stop(true);
        model.addAttribute("message", "SFTP server stopped.");
        model.addAttribute("sftpRunning", sftp.isRunning());
        return "index";
    }

    @GetMapping("/client")
    public String client(Model model) {
        return "client";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("username") String username,
                         @RequestParam("password") String password,
                         Model model) {
        client.uploadFile(username, password);
        return "client";
    }
}
