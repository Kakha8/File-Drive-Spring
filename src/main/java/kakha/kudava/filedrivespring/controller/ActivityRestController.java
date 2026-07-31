package kakha.kudava.filedrivespring.controller;
import kakha.kudava.filedrivespring.dto.ActivityItem;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.services.ResourceAccessService;
import kakha.kudava.filedrivespring.services.notifications.ActivityService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/activity")
public class ActivityRestController {

    private static final int MAX_PAGE_SIZE = 50;

    private final ActivityService activityService;
    private final ResourceAccessService resourceAccessService;

    public ActivityRestController(
            ActivityService activityService,
            ResourceAccessService resourceAccessService
    ) {
        this.activityService = activityService;
        this.resourceAccessService = resourceAccessService;
    }

    @GetMapping
    public ResponseEntity<ActivityPageResponse> getRecentActivity(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        User currentUser =
                resourceAccessService.currentUser();

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(
                Math.max(size, 1),
                MAX_PAGE_SIZE
        );

        Page<ActivityItem> result =
                activityService.getRecentActivity(
                        currentUser,
                        PageRequest.of(
                                safePage,
                                safeSize
                        )
                );

        return ResponseEntity.ok(
                new ActivityPageResponse(
                        result.getContent(),
                        result.getNumber(),
                        result.getSize(),
                        result.getTotalElements(),
                        result.getTotalPages(),
                        result.hasNext()
                )
        );
    }

    public record ActivityPageResponse(
            List<ActivityItem> activities,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }
}