package kakha.kudava.filedrivespring.controller;

import kakha.kudava.filedrivespring.dto.FileMetaDataDTO;
import kakha.kudava.filedrivespring.dto.RenameRequest;
import kakha.kudava.filedrivespring.model.FileMetaData;
import kakha.kudava.filedrivespring.services.FileService;
import kakha.kudava.filedrivespring.services.ObjectStorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLConnection;

@RestController
@RequestMapping("api/files")
public class FilesRestController {

    private final ObjectStorageService storage;
    private final FileService fileService;

    public FilesRestController(ObjectStorageService storage, FileService fileService) {
        this.storage = storage;
        this.fileService = fileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileMetaDataDTO> upload(@RequestParam("file") MultipartFile file,
                                                  @RequestParam("parentId") Long parentId) throws Exception {
        FileMetaDataDTO dto = fileService.upload(file, parentId);
        return ResponseEntity.status(201).body(dto);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) throws Exception {
        FileMetaData meta = storage.getMeta(id);
        InputStream in = storage.download(id);

        String contentType = meta.getObjectType();
        if (contentType == null || contentType.isBlank()) {
            contentType = URLConnection.guessContentTypeFromName(meta.getFileName());
        }
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + meta.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(new InputStreamResource(in));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) throws Exception {
        storage.delete(id);
        return ResponseEntity.noContent().build();
    }

    //need to add overwrite
    @PutMapping("/{id}/rename")
    public ResponseEntity<Void> rename(@PathVariable Long id, @RequestBody RenameRequest req) throws Exception {
        storage.renameFile(id, req.getNewName());
        return ResponseEntity.noContent().build();
    }
}
