package kakha.kudava.filedrivespring.controller;


import kakha.kudava.filedrivespring.dto.LockboxFolderViewResponse;
import kakha.kudava.filedrivespring.dto.LockboxUploadResponse;
import kakha.kudava.filedrivespring.records.LockboxDownloadResult;
import kakha.kudava.filedrivespring.services.lockbox.LockboxService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/lockbox")
public class LockboxRestController {

    private final LockboxService lockboxService;

    public LockboxRestController(LockboxService lockboxService) {
        this.lockboxService = lockboxService;
    }

    /**
     * Uploads an already client-encrypted CSEMLK02 container.
     *
     * Omitting parentFolderId uploads into the current user's
     * Lockbox root.
     */
    @PostMapping(
            value = "/files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public LockboxUploadResponse upload(
            @RequestPart("file")
            MultipartFile encryptedFile,

            @RequestParam(
                    name = "parentFolderId",
                    required = false
            )
            Long parentFolderId
    ) throws Exception {
        return lockboxService.upload(
                encryptedFile,
                parentFolderId
        );
    }

    /**
     * Returns the current user's Lockbox root and its direct children.
     */
    @GetMapping(
            value = "/folders",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public LockboxFolderViewResponse viewRoot() {
        return lockboxService.viewRoot();
    }

    /**
     * Returns one Lockbox folder and its direct child folders/files.
     */
    @GetMapping(
            value = "/folders/{folderId}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public LockboxFolderViewResponse viewFolder(
            @PathVariable Long folderId
    ) {
        return lockboxService.viewFolder(folderId);
    }

    /**
     * Streams the encrypted container exactly as stored.
     *
     * The server does not decrypt the downloaded object.
     */
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable Long fileId
    ) throws Exception {
        LockboxDownloadResult result =
                lockboxService.openDownload(fileId);

        StreamingResponseBody responseBody = outputStream -> {
            try (InputStream input = result.inputStream()) {
                input.transferTo(outputStream);
            }
        };

        ContentDisposition disposition =
                ContentDisposition
                        .attachment()
                        .filename(result.fileName())
                        .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(result.ciphertextSize())
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString()
                )
                .body(responseBody);
    }
}
