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
    public ResponseEntity<Map<String, ObjectStorageService.UploadResult>> upload(@RequestPart("file") MultipartFile file) throws Exception {
        ObjectStorageService.UploadResult key = storage.upload(file);
        return ResponseEntity.ok(Map.of("key", key));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) throws Exception {
        InputStream in = storage.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(in));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) throws Exception {
        storage.delete(id);
        return ResponseEntity.noContent().build();
    }
}
