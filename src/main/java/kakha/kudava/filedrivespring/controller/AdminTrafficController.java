package kakha.kudava.filedrivespring.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kakha.kudava.filedrivespring.enums.ActionType;
import kakha.kudava.filedrivespring.model.ActionLogs;
import kakha.kudava.filedrivespring.repository.ActionLogsRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@RestController
@RequestMapping("/api/admin/traffic")
public class AdminTrafficController {
    private static final int HOURS = 24;
    private static final EnumSet<ActionType> TRAFFIC_ACTIONS = EnumSet.of(
            ActionType.UPLOAD,
            ActionType.MALWARE_UPLOAD,
            ActionType.DOWNLOAD
    );

    private final ActionLogsRepository repository;
    private final ObjectMapper objectMapper;

    public AdminTrafficController(ActionLogsRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<TrafficResponse> traffic() {
        Instant end = Instant.now();
        Instant currentHour = end.truncatedTo(ChronoUnit.HOURS);
        Instant start = currentHour.minus(HOURS - 1L, ChronoUnit.HOURS);

        Specification<ActionLogs> filters = (root, ignored, builder) -> builder.and(
                root.get("action").in(TRAFFIC_ACTIONS),
                builder.greaterThanOrEqualTo(root.get("timestamp"), start),
                builder.lessThanOrEqualTo(root.get("timestamp"), end)
        );

        long[] uploaded = new long[HOURS];
        long[] downloaded = new long[HOURS];

        for (ActionLogs log : repository.findAll(filters)) {
            long bucket = ChronoUnit.HOURS.between(start, log.getTimestamp());
            if (bucket < 0 || bucket >= HOURS) continue;

            long bytes = trafficBytes(log.getDetails());
            if (bytes <= 0) continue;

            if (log.getAction() == ActionType.DOWNLOAD) {
                downloaded[(int) bucket] = saturatedAdd(downloaded[(int) bucket], bytes);
            } else {
                uploaded[(int) bucket] = saturatedAdd(uploaded[(int) bucket], bytes);
            }
        }

        List<TrafficPoint> points = new ArrayList<>(HOURS);
        long totalUploaded = 0;
        long totalDownloaded = 0;
        for (int index = 0; index < HOURS; index++) {
            totalUploaded = saturatedAdd(totalUploaded, uploaded[index]);
            totalDownloaded = saturatedAdd(totalDownloaded, downloaded[index]);
            points.add(new TrafficPoint(
                    start.plus(index, ChronoUnit.HOURS),
                    uploaded[index],
                    downloaded[index],
                    saturatedAdd(uploaded[index], downloaded[index])
            ));
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new TrafficResponse(
                        start,
                        end,
                        totalUploaded,
                        totalDownloaded,
                        saturatedAdd(totalUploaded, totalDownloaded),
                        points
                ));
    }

    private long trafficBytes(String details) {
        if (details == null || details.isBlank()) return 0;
        try {
            JsonNode node = objectMapper.readTree(details);
            if (node.isTextual()) node = objectMapper.readTree(node.asText());
            long size = nonNegativeLong(node, "size");
            return size > 0 ? size : nonNegativeLong(node, "zipSizeBytes");
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static long nonNegativeLong(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.canConvertToLong() ? Math.max(0, value.asLong()) : 0;
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    public record TrafficPoint(Instant start, long uploadedBytes, long downloadedBytes, long totalBytes) {
    }

    public record TrafficResponse(
            Instant start,
            Instant end,
            long uploadedBytes,
            long downloadedBytes,
            long totalBytes,
            List<TrafficPoint> points
    ) {
    }
}
