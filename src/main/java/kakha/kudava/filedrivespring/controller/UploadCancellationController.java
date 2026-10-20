package kakha.kudava.filedrivespring.controller;

import kakha.kudava.filedrivespring.services.UploadCancellationService;
import kakha.kudava.filedrivespring.services.ResourceAccessService;
import kakha.kudava.filedrivespring.services.TrafficSamplingService;
import kakha.kudava.filedrivespring.services.UploadPipelineMonitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/uploads")
public class UploadCancellationController {

    private final UploadCancellationService uploadCancellationService;
    private final TrafficSamplingService trafficSamplingService;
    private final ResourceAccessService access;
    private final UploadPipelineMonitor uploadPipeline;

    public UploadCancellationController(UploadCancellationService uploadCancellationService,
                                        TrafficSamplingService trafficSamplingService,
                                        ResourceAccessService access,
                                        UploadPipelineMonitor uploadPipeline) {
        this.uploadCancellationService = uploadCancellationService;
        this.trafficSamplingService = trafficSamplingService;
        this.access = access;
        this.uploadPipeline = uploadPipeline;
    }

    @DeleteMapping("/{uploadId}")
    public ResponseEntity<Void> cancelUpload(@PathVariable String uploadId) {
        log.info("Cancel upload requested: uploadId={}", uploadId);
        uploadCancellationService.cancel(uploadId);
        uploadPipeline.stage(uploadId, UploadPipelineMonitor.Stage.CANCELED);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{uploadId}/progress")
    public ResponseEntity<Void> reportProgress(@PathVariable String uploadId,
                                               @RequestBody UploadProgressRequest request) {
        long total = Math.max(0, request.totalBytes());
        long uploaded = Math.max(0, Math.min(request.uploadedBytes(), total));
        trafficSamplingService.reportUploadProgress(access.currentUser(), uploadId, uploaded);
        uploadPipeline.receiving(uploadId, uploaded, total);
        return ResponseEntity.noContent().build();
    }

    public record UploadProgressRequest(long uploadedBytes, long totalBytes) {}

}
