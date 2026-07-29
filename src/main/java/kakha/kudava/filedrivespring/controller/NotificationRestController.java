package kakha.kudava.filedrivespring.controller;

import jakarta.persistence.EntityNotFoundException;
import kakha.kudava.filedrivespring.enums.EntityType;
import kakha.kudava.filedrivespring.enums.NotificationType;
import kakha.kudava.filedrivespring.model.Notification;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.services.NotificationService;
import kakha.kudava.filedrivespring.services.ResourceAccessService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationRestController {

    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationService notificationService;
    private final ResourceAccessService resourceAccessService;

    public NotificationRestController(
            NotificationService notificationService,
            ResourceAccessService resourceAccessService
    ) {
        this.notificationService = notificationService;
        this.resourceAccessService = resourceAccessService;
    }

    /**
     * GET /api/notifications?page=0&size=10
     */
    @GetMapping
    public ResponseEntity<NotificationPageResponse> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        User recipient = resourceAccessService.currentUser();

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(
                Math.max(size, 1),
                MAX_PAGE_SIZE
        );

        Page<Notification> result =
                notificationService.getNotifications(
                        recipient,
                        PageRequest.of(safePage, safeSize)
                );

        List<NotificationResponse> notifications =
                result.getContent()
                        .stream()
                        .map(NotificationResponse::from)
                        .toList();

        return ResponseEntity.ok(
                new NotificationPageResponse(
                        notifications,
                        result.getNumber(),
                        result.getSize(),
                        result.getTotalElements(),
                        result.getTotalPages(),
                        result.hasNext()
                )
        );
    }

    /**
     * GET /api/notifications/unread-count
     */
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount() {
        User recipient = resourceAccessService.currentUser();

        long unreadCount =
                notificationService.getUnreadCount(recipient);

        return ResponseEntity.ok(
                new UnreadCountResponse(unreadCount)
        );
    }

    /**
     * POST /api/notifications/{notificationId}/read
     */
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markRead(
            @PathVariable Long notificationId
    ) {
        User recipient = resourceAccessService.currentUser();

        Notification notification =
                notificationService.markRead(
                        notificationId,
                        recipient
                );

        return ResponseEntity.ok(
                NotificationResponse.from(notification)
        );
    }

    /**
     * POST /api/notifications/read-all
     */
    @PostMapping("/read-all")
    public ResponseEntity<MarkAllReadResponse> markAllRead() {
        User recipient = resourceAccessService.currentUser();

        int updated =
                notificationService.markAllRead(recipient);

        return ResponseEntity.ok(
                new MarkAllReadResponse(updated)
        );
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(
            EntityNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "message",
                        exception.getMessage() == null
                                ? "Notification not found"
                                : exception.getMessage()
                ));
    }

    public record NotificationResponse(
            Long id,
            NotificationType type,
            String title,
            String message,
            EntityType entityType,
            Long entityId,
            boolean read,
            Instant createdAt,
            Instant readAt
    ) {
        public static NotificationResponse from(
                Notification notification
        ) {
            return new NotificationResponse(
                    notification.getId(),
                    notification.getType(),
                    notification.getTitle(),
                    notification.getMessage(),
                    notification.getEntityType(),
                    notification.getEntityId(),
                    notification.isRead(),
                    notification.getCreatedAt(),
                    notification.getReadAt()
            );
        }
    }

    public record NotificationPageResponse(
            List<NotificationResponse> notifications,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }

    public record UnreadCountResponse(
            long unreadCount
    ) {
    }

    public record MarkAllReadResponse(
            int updated
    ) {
    }
}