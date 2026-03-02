package kakha.kudava.filedrivespring.controller;

import io.minio.errors.*;
import kakha.kudava.filedrivespring.dto.FolderDTO;
import kakha.kudava.filedrivespring.model.Folders;
import kakha.kudava.filedrivespring.services.FolderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

@RestController
@RequestMapping("/api/directories")
public class FoldersRestController {

    private final FolderService folderService;

    public FoldersRestController(FolderService folderService) {
        this.folderService = folderService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody FolderDTO folderDTO)
            throws ServerException,
            InsufficientDataException,
            ErrorResponseException,
            IOException,
            NoSuchAlgorithmException,
            InvalidKeyException,
            InvalidResponseException, XmlParserException, InternalException {
        FolderDTO folder = folderService.create(folderDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("name", folder.getName(),
                        "prefix", folder.getPrefix()));
    }

}
