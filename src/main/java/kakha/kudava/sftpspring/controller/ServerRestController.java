package kakha.kudava.sftpspring.controller;

import jakarta.servlet.http.HttpSession;
import kakha.kudava.sftpspring.model.User;
import kakha.kudava.sftpspring.repository.UserRepository;
import kakha.kudava.sftpspring.services.SftpClientService;
import kakha.kudava.sftpspring.services.SftpServerService;
import kakha.kudava.sftpspring.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ServerRestController {

    private final UserRepository userRepository;
    private final SftpClientService sftpClientService;

    public ServerRestController(UserRepository userRepository,
                                SftpClientService sftpClientService) {
        this.userRepository = userRepository;
        this.sftpClientService = sftpClientService;
    }

/*    @GetMapping("/sftpuser")
    public Map<String, Object> getSftpUser(HttpSession session) {
        Object sftpUser = session.getAttribute("sftpUser");
        String conTime = sftpClientService.getTime();


        Map<String, Object> response = new HashMap<>();
        response.put("sftpuser", sftpUser != null ? sftpUser.toString() : null);
        response.put("time", conTime);

        //System.out.println(sftpClientService.getTime());

        return response;
    }*/

    @GetMapping("/sftpuser")
    public Map<String, Object> getSftpUser(HttpSession session) {
        // Fetch all known usernames
        List<String> users = userRepository.getUsernames();

        // The currently connected user stored in this session
        Object currentSftpUser = session.getAttribute("sftpUser");

        List<Map<String, Object>> userList = new ArrayList<>();

        for (String username : users) {
            boolean connected = currentSftpUser != null && username.equals(currentSftpUser.toString());
            String conTime = connected ? sftpClientService.getTime() : null;

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("username", username);
            userInfo.put("connected", connected);
            userInfo.put("time", conTime);

            userList.add(userInfo);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("users", userList);
        response.put("timestamp", sftpClientService.getTime()); // optional global time marker

        return response;
    }

}
