package kakha.kudava.filedrivespring.controller;

import kakha.kudava.filedrivespring.dto.ViewQuarantinedFilesDTO;
import kakha.kudava.filedrivespring.model.QuarantinedFiles;
import kakha.kudava.filedrivespring.repository.QuarantinedFilesRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/quarantine")
public class AdminQuarantineController {
    private static final int MAX_PAGE_SIZE = 50;

    private final QuarantinedFilesRepository repository;

    public AdminQuarantineController(QuarantinedFilesRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<QuarantinePageResponse> quarantinedFiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean all,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) List<String> users,
            @RequestParam(required = false) List<String> signatures,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be later than 'to'");
        }

        Specification<QuarantinedFiles> filters = filters(query, users, signatures, from, to);

        List<ViewQuarantinedFilesDTO> items;
        long totalElements;
        int responsePage;
        int responseSize;
        int totalPages;
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt");

        if (all) {
            items = repository.findAll(filters, newestFirst).stream().map(AdminQuarantineController::toDto).toList();
            totalElements = items.size();
            responsePage = 0;
            responseSize = items.size();
            totalPages = items.isEmpty() ? 0 : 1;
        } else {
            Page<QuarantinedFiles> result = repository.findAll(
                    filters,
                    PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), newestFirst)
            );
            items = result.getContent().stream().map(AdminQuarantineController::toDto).toList();
            totalElements = result.getTotalElements();
            responsePage = result.getNumber();
            responseSize = result.getSize();
            totalPages = result.getTotalPages();
        }

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                new QuarantinePageResponse(items, responsePage, responseSize, totalElements, totalPages)
        );
    }

    @GetMapping("/signatures")
    public ResponseEntity<List<String>> signatures() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(repository.findActiveSignatures());
    }

    @GetMapping("/timeline")
    public ResponseEntity<QuarantineTimelineResponse> timeline(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) List<String> users,
            @RequestParam(required = false) List<String> signatures,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be later than 'to'");
        }

        Instant now = Instant.now();
        Instant rangeStart = from == null ? now.minus(13, ChronoUnit.DAYS) : from;
        Instant rangeEnd = to == null ? now : to;
        long rangeSeconds = Math.max(1, rangeEnd.getEpochSecond() - rangeStart.getEpochSecond());
        long bucketSeconds = Math.max(3_600, ((rangeSeconds + 19) / 20 + 3_599) / 3_600 * 3_600);
        long alignedStartSeconds = Math.floorDiv(rangeStart.getEpochSecond(), bucketSeconds) * bucketSeconds;
        Instant alignedStart = Instant.ofEpochSecond(alignedStartSeconds);
        int bucketCount = (int) Math.min(21,
                Math.max(1, (rangeEnd.getEpochSecond() - alignedStartSeconds) / bucketSeconds + 1));

        Long[] counts = new Long[bucketCount];
        java.util.Arrays.fill(counts, 0L);
        List<QuarantinedFiles> matches = repository.findAll(
                filters(query, users, signatures, rangeStart, rangeEnd)
        );
        for (QuarantinedFiles file : matches) {
            long index = (file.getCreatedAt().getEpochSecond() - alignedStartSeconds) / bucketSeconds;
            if (index >= 0 && index < bucketCount) counts[(int) index]++;
        }

        List<QuarantineTimelinePoint> points = new ArrayList<>(bucketCount);
        for (int index = 0; index < bucketCount; index++) {
            Instant bucketStart = alignedStart.plusSeconds(index * bucketSeconds);
            points.add(new QuarantineTimelinePoint(bucketStart, counts[index]));
        }

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                new QuarantineTimelineResponse(points, matches.size(), bucketSeconds)
        );
    }

    private static Specification<QuarantinedFiles> filters(
            String query,
            List<String> users,
            List<String> signatures,
            Instant from,
            Instant to
    ) {
        Specification<QuarantinedFiles> filters = (root, ignored, builder) -> builder.isFalse(root.get("deleted"));
        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase() + "%";
            filters = filters.and((root, ignored, builder) ->
                    builder.like(builder.lower(root.get("originalFilename")), pattern));
        }
        if (users != null && !users.isEmpty()) {
            filters = filters.and((root, ignored, builder) -> root.join("user").get("username").in(users));
        }
        if (signatures != null && !signatures.isEmpty()) {
            filters = filters.and((root, ignored, builder) -> root.get("clamAvResponse").in(signatures));
        }
        if (from != null) {
            filters = filters.and((root, ignored, builder) ->
                    builder.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            filters = filters.and((root, ignored, builder) ->
                    builder.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        return filters;
    }

    private static ViewQuarantinedFilesDTO toDto(QuarantinedFiles file) {
        return new ViewQuarantinedFilesDTO(
                file.getId(), file.getOriginalFilename(), file.getObjectKey(), file.getContentType(), file.getSize(),
                file.getChecksum(), file.getClamAvResponse(), file.getParentFolderId(), file.getUser().getId(),
                file.getUser().getUsername(), file.getCreatedAt()
        );
    }

    public record QuarantinePageResponse(
            List<ViewQuarantinedFilesDTO> files,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record QuarantineTimelinePoint(Instant start, long count) {
    }

    public record QuarantineTimelineResponse(
            List<QuarantineTimelinePoint> points,
            long totalDetections,
            long bucketSeconds
    ) {
    }
}
