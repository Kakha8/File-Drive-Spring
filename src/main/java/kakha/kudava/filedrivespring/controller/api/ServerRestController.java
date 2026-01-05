package kakha.kudava.filedrivespring.controller.api;

import jakarta.servlet.http.HttpSession;
import kakha.kudava.filedrivespring.repository.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ServerRestController {

    private final UserRepository userRepository;

    public ServerRestController(UserRepository userRepository) {
        this.userRepository = userRepository;
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
    

}
