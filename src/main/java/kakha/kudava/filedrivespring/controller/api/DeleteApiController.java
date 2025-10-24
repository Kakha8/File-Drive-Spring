package kakha.kudava.filedrivespring.controller.api;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@RestController
public class DeleteApiController {

    @GetMapping("/api/delete")
    public Object deleteFile(HttpSession session, String filePath) {
        Map<String, Object> response = new HashMap<>();
        String user = (String) session.getAttribute("USERNAME");
        String fullPath = "sftp-root/" + user + "/" + filePath;

        File file = new File(fullPath);
        if(file.delete()) {
            response.put("file", filePath);
            response.put("status", true);
        } else {
            response.put("file", filePath);
            response.put("status", false);
        }

        return null;
    }

}
