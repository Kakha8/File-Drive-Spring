package kakha.kudava.filedrivespring.controller;

import kakha.kudava.filedrivespring.dto.DeleteFilesReqDTO;
import kakha.kudava.filedrivespring.dto.FileMetaDataDTO;
import kakha.kudava.filedrivespring.dto.MoveFileRequest;
import kakha.kudava.filedrivespring.dto.RenameRequest;
import kakha.kudava.filedrivespring.model.FileMetaData;
import kakha.kudava.filedrivespring.services.MoveService;
import kakha.kudava.filedrivespring.services.UploadCancellationService;
import kakha.kudava.filedrivespring.services.objects.FileService;
import kakha.kudava.filedrivespring.services.ObjectStorageService;
import kakha.kudava.filedrivespring.services.RenameService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLConnection;
import java.util.List;

@RestController
@RequestMapping("api/files")
public class FilesRestController {

    private final ObjectStorageService storage;
    private final FileService fileService;
    private final RenameService renameService;
    private final MoveService moveService;
    private final UploadCancellationService uploadCancellationService;
    private final ObjectStorageService objectStorageService;

    public FilesRestController(ObjectStorageService storage, FileService fileService, RenameService renameService, MoveService moveService, UploadCancellationService uploadCancellationService, ObjectStorageService objectStorageService) {
        this.storage = storage;
        this.fileService = fileService;
        this.renameService = renameService;
        this.moveService = moveService;
        this.uploadCancellationService = uploadCancellationService;
        this.objectStorageService = objectStorageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileMetaDataDTO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parentId", required = false) Long parentId,
            @RequestParam(value = "uploadId", required = false) String uploadId
    ) throws Exception {
        FileMetaDataDTO dto = fileService.upload(file, parentId, uploadId);
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

    @DeleteMapping("/delete/multiple")
    public ResponseEntity<Void> deleteMultiple(@RequestBody DeleteFilesReqDTO reqDTO) throws Exception {
        objectStorageService.deleteMultipleFiles(reqDTO.getFileIds());
        return ResponseEntity.noContent().build();
    }

    //need to add overwrite
    @PutMapping("/{id}/rename")
    public ResponseEntity<Void> rename(@PathVariable Long id, @RequestBody RenameRequest req) throws Exception {
        renameService.renameFile(id, req.getNewName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/move")
    public ResponseEntity<Void> move(@PathVariable Long id, @RequestBody MoveFileRequest req) throws Exception {
        moveService.moveFile(id, req.getTargetFolderId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/copy")
    public ResponseEntity<Void> copy(@PathVariable Long id, @RequestBody MoveFileRequest req) throws Exception {
        moveService.copyFile(id, req.getTargetFolderId());
        return ResponseEntity.noContent().build();
    }
}
