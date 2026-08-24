package kakha.kudava.filedrivespring.controller;

import kakha.kudava.filedrivespring.dto.lockbox.LockboxCreateShareRequest;
import kakha.kudava.filedrivespring.dto.lockbox.LockboxRecipientKeysResponse;
import kakha.kudava.filedrivespring.dto.lockbox.LockboxReceivedShareResponse;
import kakha.kudava.filedrivespring.dto.lockbox.LockboxReceivedSharesResponse;
import kakha.kudava.filedrivespring.dto.lockbox.LockboxShareResponse;
import kakha.kudava.filedrivespring.records.LockboxDownloadResult;
import kakha.kudava.filedrivespring.services.lockbox.LockboxSharingService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.util.UUID;

@RestController
@RequestMapping("/api/lockbox")
public class LockboxSharingController {

    private final LockboxSharingService sharingService;

    public LockboxSharingController(
            LockboxSharingService sharingService
    ) {
        this.sharingService = sharingService;
    }

    @GetMapping(
            value = "/share-recipients/{username}/keys",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<LockboxRecipientKeysResponse>
    recipientKeys(
            @PathVariable String username
    ) {
        return ResponseEntity.ok()
                .cacheControl(
                        CacheControl.noStore()
                )
                .body(
                        sharingService
                                .recipientEncryptionKeys(
                                        username
                                )
                );
    }

    @PostMapping(
            value = "/shares",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<LockboxShareResponse> createShare(
            @RequestBody LockboxCreateShareRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(sharingService.createShare(request));
    }

    @GetMapping(
            value = "/shares/received",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<LockboxReceivedSharesResponse> receivedShares()
            throws Exception {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(sharingService.receivedShares());
    }

    @GetMapping(
            value = "/shares/received/{shareUuid}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<LockboxReceivedShareResponse> receivedShare(
            @PathVariable UUID shareUuid
    ) throws Exception {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(sharingService.receivedShare(shareUuid));
    }

    @GetMapping("/shares/received/{shareUuid}/container")
    public ResponseEntity<StreamingResponseBody>
    downloadReceivedContainer(
            @PathVariable UUID shareUuid
    ) throws Exception {

        LockboxDownloadResult result =
                sharingService.openReceivedContainer(
                        shareUuid
                );

        StreamingResponseBody responseBody =
                outputStream -> {
                    try (InputStream input =
                                 result.inputStream()) {

                        input.transferTo(outputStream);
                    }
                };

        ContentDisposition disposition =
                ContentDisposition
                        .attachment()
                        .filename(result.fileName())
                        .build();

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                result.contentType()
                        )
                )
                .contentLength(result.size())
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString()
                )
                .body(responseBody);
    }
}
