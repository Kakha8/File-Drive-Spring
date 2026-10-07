package kakha.kudava.filedrivespring.controller;

import kakha.kudava.filedrivespring.enums.ActionType;
import kakha.kudava.filedrivespring.enums.EntityType;
import kakha.kudava.filedrivespring.model.ActionLogs;
import kakha.kudava.filedrivespring.repository.ActionLogsRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/logs")
public class AdminLogsController {
    private static final int MAX_PAGE_SIZE = 50;

    private final ActionLogsRepository actionLogsRepository;

    public AdminLogsController(ActionLogsRepository actionLogsRepository) {
        this.actionLogsRepository = actionLogsRepository;
    }

    @GetMapping
    public ResponseEntity<AdminLogsResponse> logs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean all,
            @RequestParam(required = false) List<String> users,
            @RequestParam(required = false) Set<ActionType> actions,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be later than 'to'");
        }

        Specification<ActionLogs> filters = Specification.unrestricted();
        if (users != null && !users.isEmpty()) {
            filters = filters.and((root, query, builder) ->
                    root.join("user").get("username").in(users));
        }
        if (actions != null && !actions.isEmpty()) {
            filters = filters.and((root, query, builder) ->
                    root.get("action").in(actions));
        }
        if (from != null) {
            filters = filters.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("timestamp"), from));
        }
        if (to != null) {
            filters = filters.and((root, query, builder) ->
                    builder.lessThanOrEqualTo(root.get("timestamp"), to));
        }

        List<AdminLogItem> items;
        long totalElements;
        int responsePage;
        int responseSize;
        int totalPages;

        if (all) {
            items = actionLogsRepository.findAll(filters, Sort.by(Sort.Direction.DESC, "timestamp"))
                    .stream()
                    .map(AdminLogsController::toItem)
                    .toList();
            totalElements = items.size();
            responsePage = 0;
            responseSize = items.size();
            totalPages = items.isEmpty() ? 0 : 1;
        } else {
            int safePage = Math.max(page, 0);
            int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
            Page<ActionLogs> result = actionLogsRepository.findAll(
                    filters,
                    PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "timestamp"))
            );
            items = result.getContent().stream().map(AdminLogsController::toItem).toList();
            totalElements = result.getTotalElements();
            responsePage = result.getNumber();
            responseSize = result.getSize();
            totalPages = result.getTotalPages();
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new AdminLogsResponse(
                        items,
                        responsePage,
                        responseSize,
                        totalElements,
                        totalPages
                ));
    }

    @GetMapping("/actions")
    public ResponseEntity<List<ActionType>> actions() {
        return ResponseEntity.ok(List.of(ActionType.values()));
    }

    private static AdminLogItem toItem(ActionLogs log) {
        return new AdminLogItem(
                log.getId(),
                log.getUser() == null ? null : log.getUser().getUsername(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getTimestamp(),
                log.getDetails()
        );
    }

    public record AdminLogsResponse(
            List<AdminLogItem> logs,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record AdminLogItem(
            Long id,
            String username,
            ActionType action,
            EntityType entityType,
            Long entityId,
            Instant timestamp,
            String details
    ) {
    }
}
