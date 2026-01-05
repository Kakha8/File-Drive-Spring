package kakha.kudava.filedrivespring.controller.api.client;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
public class DownloadController {


    @GetMapping("/api/download/stream")
    public ResponseEntity<StreamingResponseBody> stream(@RequestParam String path,
                                                        HttpSession session) throws IOException, IOException {

        String userDir = (String) session.getAttribute("USERNAME");
        Path root = Path.of(System.getProperty("user.dir"), "sftp-root", userDir);

        Path file = root.resolve(path).normalize();
        if (!file.startsWith(root) || Files.isDirectory(file)) return ResponseEntity.notFound().build();

        var cd = ContentDisposition.attachment()
                .filename(file.getFileName().toString(), StandardCharsets.UTF_8).build();

        StreamingResponseBody body = out -> {
            try (var in = Files.newInputStream(file)) {
                in.transferTo(out); // zero-copy-ish on modern JDKs
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(file))
                .body(body);
    }

}
