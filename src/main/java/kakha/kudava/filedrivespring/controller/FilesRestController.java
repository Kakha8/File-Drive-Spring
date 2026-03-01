package kakha.kudava.filedrivespring.controller;

import kakha.kudava.filedrivespring.services.ObjectStorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLConnection;
import java.util.Map;

@RestController
@RequestMapping("api/files")
public class FilesRestController {

    private final ObjectStorageService storage;

    public FilesRestController(ObjectStorageService storage) {
        this.storage = storage;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(@RequestPart("file") MultipartFile file) throws Exception {
        String key = storage.upload(file);
        return ResponseEntity.ok(Map.of("key", key));
    }

    @GetMapping("/{key}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String key) throws Exception {
        InputStream in = storage.download(key);

        String contentType = URLConnection.guessContentTypeFromName(key);
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + key + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(new InputStreamResource(in));
    }
}
