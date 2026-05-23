package kakha.kudava.filedrivespring.controller;

import kakha.kudava.filedrivespring.dto.DownloadZipRequest;
import kakha.kudava.filedrivespring.records.ZipDownloadResult;
import kakha.kudava.filedrivespring.services.DownloadService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/download")
public class DownloadRestController {

    private final DownloadService downloadService;

    public DownloadRestController(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    @PostMapping("/zip")
    public ResponseEntity<InputStreamResource> downloadZip(
            @RequestBody DownloadZipRequest request
    ) throws Exception {
        ZipDownloadResult result = downloadService.downloadAsZip(request);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + result.fileName() + "\""
                )
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(result.inputStream()));
    }
}