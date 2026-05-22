package kakha.kudava.filedrivespring.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.minio.errors.*;
import kakha.kudava.filedrivespring.dto.*;
import kakha.kudava.filedrivespring.model.FileMetaData;
import kakha.kudava.filedrivespring.model.Folders;
import kakha.kudava.filedrivespring.records.FolderDownloadResult;
import kakha.kudava.filedrivespring.repository.FileMetaDataRepository;
import kakha.kudava.filedrivespring.repository.FolderRepository;
import kakha.kudava.filedrivespring.services.MoveService;
import kakha.kudava.filedrivespring.services.objects.FolderService;
import kakha.kudava.filedrivespring.services.RenameService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
public class FoldersRestController {

    private final FolderService folderService;
    private final FolderRepository folderRepository;
    private final FileMetaDataRepository fileMetaDataRepository;
    private final RenameService renameService;
    private final MoveService moveService;

    public FoldersRestController(FolderService folderService, FolderRepository folderRepository, FileMetaDataRepository fileMetaDataRepository, RenameService renameService, MoveService moveService) {
        this.folderService = folderService;
        this.folderRepository = folderRepository;
        this.fileMetaDataRepository = fileMetaDataRepository;
        this.renameService = renameService;
        this.moveService = moveService;
    }

    @GetMapping
    public List<Folders> list() {
        return folderRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<FolderViewDTO> get(@PathVariable Long id) {
        List<FolderItemDTO> folders = folderService.viewFolders(id);
        List<FileItemDTO> files = folderService.viewFiles(id);

        FolderViewDTO dto = new FolderViewDTO();
        dto.setFolders(folders);
        dto.setFiles(files);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) throws Exception {
        FolderDownloadResult result = folderService.downloadFolderAsZip(id);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + result.fileName() + "\""
                )
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(result.inputStream()));
    }

    @GetMapping("/root")
    public ResponseEntity<FolderViewDTO> getRoot(Authentication authentication) {
        return ResponseEntity.ok(folderService.viewCurrentUserRoot(authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody FolderCreateRequest req)
            throws Exception {
        FolderDTO folder = folderService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("name", folder.getName(),
                        "prefix", folder.getPrefix()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable("id") Long id) throws InsufficientDataException,
            ErrorResponseException,
            IOException, NoSuchAlgorithmException,
            InvalidKeyException, InstantiationException, IllegalAccessException {
        folderService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PutMapping("/{id}/rename")
    public ResponseEntity<Void> rename(@PathVariable Long id, @RequestBody RenameRequest req) throws InsufficientDataException,
            ErrorResponseException, JsonProcessingException {
        renameService.renameFolder(id, req.getNewName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/move")
    public ResponseEntity<Void> moveFolder(@PathVariable Long id, @RequestBody MoveFolderRequest req){
        moveService.moveFolder(id, req.getTargetFolderId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/copy")
    public ResponseEntity<Void> copyFolder(@PathVariable Long id, @RequestBody MoveFolderRequest req) {
        moveService.copyFolder(id, req.getTargetFolderId());
        return ResponseEntity.noContent().build();
    }

}
