package kakha.kudava.filedrivespring.controller.api;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@RestController
public class UploadApiController {


    @PostMapping(value = "/api/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestPart("file") MultipartFile file,
                                    HttpSession session, @RequestParam String selectedPath) throws IOException {
        String user = (String) session.getAttribute("USERNAME");
        String uploadDir = "";

        if(selectedPath.isEmpty()) {
            uploadDir = "sftp-root/" + user;
        } else
            uploadDir = "sftp-root/" + user + "/" + selectedPath;


        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    java.util.Map.of("ok", false, "message", "Choose a file to upload.")
            );
        }
        String filename = StringUtils.cleanPath(file.getOriginalFilename());
        Path target = Path.of(uploadDir).resolve(filename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        return ResponseEntity.ok(java.util.Map.of(
                "ok", true,
                "fileName", filename,
                "bytes", file.getSize(),
                "savedPath", target.toAbsolutePath().toString()
        ));
    }
}
