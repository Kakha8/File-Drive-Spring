package kakha.kudava.filedrivespring.controller;

import kakha.kudava.filedrivespring.dto.LockboxRecipientKeysResponse;
import kakha.kudava.filedrivespring.services.lockbox.LockboxSharingService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lockbox/share-recipients")
public class LockboxSharingController {

    private final LockboxSharingService sharingService;

    public LockboxSharingController(
            LockboxSharingService sharingService
    ) {
        this.sharingService = sharingService;
    }

    @GetMapping(
            value = "/{username}/keys",
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
}