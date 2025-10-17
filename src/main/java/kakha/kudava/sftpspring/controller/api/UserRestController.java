package kakha.kudava.sftpspring.controller.api;

import kakha.kudava.sftpspring.services.ServerSessionRegistry;
import kakha.kudava.sftpspring.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user-info")
public class UserRestController {

    @Autowired
    private final UserService userService;
    @Autowired
    private final ServerSessionRegistry sessionRegistry;

    public UserRestController(UserService userService, ServerSessionRegistry sessionRegistry) {
        this.userService = userService;
        this.sessionRegistry = sessionRegistry;
    }


    @GetMapping("/all-user")
    public Map<String, Object> allUser() {
        List<String> users = userService.getUsernames();

        Map<String, Object> response = new HashMap<>();
        for (String user : users) {
            if(sessionRegistry.isUserConnected(user)){
                System.out.println(user + " is connected");
                response.put(user, true);
            }
            else {
                System.out.println(user + " is not connected");
                response.put(user, false);
            }
        }

        return response;
    }
}
