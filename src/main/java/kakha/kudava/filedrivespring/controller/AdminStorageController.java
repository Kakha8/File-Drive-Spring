package kakha.kudava.filedrivespring.controller;

import kakha.kudava.filedrivespring.services.StorageQuotaService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/storage")
public class AdminStorageController {
    private final StorageQuotaService storageQuotaService;

    public AdminStorageController(StorageQuotaService storageQuotaService) {
        this.storageQuotaService = storageQuotaService;
    }

    @GetMapping
    public ResponseEntity<StorageUsageResponse> usage() {
        StorageQuotaService.Usage usage = storageQuotaService.usage();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new StorageUsageResponse(
                        usage.usedBytes(),
                        usage.limitBytes(),
                        usage.availableBytes(),
                        usage.usedPercentage(),
                        usage.objectCount(),
                        new StorageCategoryResponse(usage.files().bytes(), usage.files().count()),
                        new StorageCategoryResponse(usage.trash().bytes(), usage.trash().count()),
                        new StorageCategoryResponse(usage.quarantine().bytes(), usage.quarantine().count()),
                        new StorageCategoryResponse(usage.lockbox().bytes(), usage.lockbox().count())
                ));
    }

    public record StorageUsageResponse(
            long usedBytes,
            long limitBytes,
            long availableBytes,
            double usedPercentage,
            long objectCount,
            StorageCategoryResponse files,
            StorageCategoryResponse trash,
            StorageCategoryResponse quarantine,
            StorageCategoryResponse lockbox
    ) {
    }

    public record StorageCategoryResponse(long bytes, long count) {}
}
