package kakha.kudava.filedrivespring.controller;

import io.minio.errors.*;
import kakha.kudava.filedrivespring.dto.*;
import kakha.kudava.filedrivespring.model.FileMetaData;
import kakha.kudava.filedrivespring.model.Folders;
import kakha.kudava.filedrivespring.repository.FileMetaDataRepository;
import kakha.kudava.filedrivespring.repository.FolderRepository;
import kakha.kudava.filedrivespring.services.FolderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
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

    public FoldersRestController(FolderService folderService, FolderRepository folderRepository, FileMetaDataRepository fileMetaDataRepository) {
        this.folderService = folderService;
        this.folderRepository = folderRepository;
        this.fileMetaDataRepository = fileMetaDataRepository;
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


    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody FolderCreateRequest req)
            throws ServerException,
            InsufficientDataException,
            ErrorResponseException,
            IOException,
            NoSuchAlgorithmException,
            InvalidKeyException,
            InvalidResponseException, XmlParserException, InternalException {
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

}
