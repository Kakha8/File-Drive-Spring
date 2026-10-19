package kakha.kudava.filedrivespring.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kakha.kudava.filedrivespring.enums.ActionType;
import kakha.kudava.filedrivespring.enums.EntityType;
import kakha.kudava.filedrivespring.model.ActionLogs;
import kakha.kudava.filedrivespring.repository.ActionLogsRepository;
import kakha.kudava.filedrivespring.model.TrafficSample;
import kakha.kudava.filedrivespring.repository.TrafficSampleRepository;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

@RestController
@RequestMapping("/api/admin/traffic")
public class AdminTrafficController {
    private static final long HOUR_SECONDS = 3_600;
    private static final int DEFAULT_HOURS = 24;
    private static final EnumSet<ActionType> TRAFFIC_ACTIONS = EnumSet.of(
            ActionType.UPLOAD, ActionType.MALWARE_UPLOAD, ActionType.DOWNLOAD
    );

    private final ActionLogsRepository repository;
    private final ObjectMapper objectMapper;
    private final TrafficSampleRepository trafficSamples;

    public AdminTrafficController(ActionLogsRepository repository, ObjectMapper objectMapper,
                                  TrafficSampleRepository trafficSamples) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.trafficSamples = trafficSamples;
    }

    @GetMapping
    public ResponseEntity<TrafficResponse> traffic(
            @RequestParam(required = false) List<String> users,
            @RequestParam(required = false) Set<TransferDirection> directions,
            @RequestParam(required = false) Set<TransferType> types,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(DEFAULT_HOURS, ChronoUnit.HOURS) : from;
        if (!start.isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must be earlier than 'to'");
        }

        Specification<ActionLogs> filters = (root, ignored, builder) -> builder.and(
                root.get("action").in(actionsFor(directions)),
                builder.greaterThanOrEqualTo(root.get("timestamp"), start),
                builder.lessThanOrEqualTo(root.get("timestamp"), end)
        );
        if (users != null && !users.isEmpty()) {
            filters = filters.and((root, ignored, builder) -> root.join("user").get("username").in(users));
        }

        long bucketSeconds = chooseBucketSeconds(start, end);
        long alignedStartSeconds = Math.floorDiv(start.getEpochSecond(), bucketSeconds) * bucketSeconds;
        Instant alignedStart = Instant.ofEpochSecond(alignedStartSeconds);
        int bucketCount = Math.toIntExact(Math.max(
                1, (end.getEpochSecond() - alignedStartSeconds) / bucketSeconds + 1
        ));
        long[] uploaded = new long[bucketCount];
        long[] downloaded = new long[bucketCount];
        long transferCount = 0;

        for (ActionLogs log : repository.findAll(filters)) {
            if (isSampled(log.getDetails())) continue;
            TransferType type = transferType(log);
            if (types != null && !types.isEmpty() && !types.contains(type)) continue;
            long bytes = trafficBytes(log.getDetails());
            if (bytes <= 0) continue;

            int bucket = (int) ((log.getTimestamp().getEpochSecond() - alignedStartSeconds) / bucketSeconds);
            if (bucket < 0 || bucket >= bucketCount) continue;
            if (log.getAction() == ActionType.DOWNLOAD) {
                downloaded[bucket] = saturatedAdd(downloaded[bucket], bytes);
            } else {
                uploaded[bucket] = saturatedAdd(uploaded[bucket], bytes);
            }
            transferCount++;
        }

        Set<String> sampledTransferIds = new HashSet<>();
        for (TrafficSample sample : trafficSamples.findByOccurredAtBetweenOrderByOccurredAtAsc(start, end)) {
            if (users != null && !users.isEmpty() && !users.contains(sample.getUser().getUsername())) continue;
            TransferDirection direction;
            TransferType type;
            try {
                direction = TransferDirection.valueOf(sample.getDirection());
                type = TransferType.valueOf(sample.getTransferType());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (directions != null && !directions.isEmpty() && !directions.contains(direction)) continue;
            if (types != null && !types.isEmpty() && !types.contains(type)) continue;
            int bucket = (int) ((sample.getOccurredAt().getEpochSecond() - alignedStartSeconds) / bucketSeconds);
            if (bucket < 0 || bucket >= bucketCount || sample.getBytes() <= 0) continue;
            if (direction == TransferDirection.DOWNLOAD) downloaded[bucket] = saturatedAdd(downloaded[bucket], sample.getBytes());
            else uploaded[bucket] = saturatedAdd(uploaded[bucket], sample.getBytes());
            sampledTransferIds.add(sample.getTransferId());
        }
        transferCount += sampledTransferIds.size();

        List<TrafficPoint> points = new ArrayList<>(bucketCount);
        long totalUploaded = 0;
        long totalDownloaded = 0;
        long peakBytes = 0;
        Instant peakAt = null;
        for (int index = 0; index < bucketCount; index++) {
            long total = saturatedAdd(uploaded[index], downloaded[index]);
            totalUploaded = saturatedAdd(totalUploaded, uploaded[index]);
            totalDownloaded = saturatedAdd(totalDownloaded, downloaded[index]);
            Instant bucketStart = alignedStart.plusSeconds(index * bucketSeconds);
            if (total > peakBytes) {
                peakBytes = total;
                peakAt = bucketStart;
            }
            points.add(new TrafficPoint(bucketStart, uploaded[index], downloaded[index], total));
        }

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new TrafficResponse(
                start, end, bucketSeconds, totalUploaded, totalDownloaded,
                saturatedAdd(totalUploaded, totalDownloaded), transferCount,
                peakBytes, peakAt, points
        ));
    }

    private static EnumSet<ActionType> actionsFor(Set<TransferDirection> directions) {
        if (directions == null || directions.isEmpty()) return TRAFFIC_ACTIONS.clone();
        EnumSet<ActionType> actions = EnumSet.noneOf(ActionType.class);
        if (directions.contains(TransferDirection.UPLOAD)) {
            actions.add(ActionType.UPLOAD);
            actions.add(ActionType.MALWARE_UPLOAD);
        }
        if (directions.contains(TransferDirection.DOWNLOAD)) actions.add(ActionType.DOWNLOAD);
        return actions;
    }

    private static TransferType transferType(ActionLogs log) {
        if (log.getAction() == ActionType.MALWARE_UPLOAD) return TransferType.MALWARE;
        if (log.getAction() == ActionType.DOWNLOAD
                && (log.getEntityType() == EntityType.BULK || log.getEntityType() == EntityType.FOLDER)) {
            return TransferType.ZIP;
        }
        return TransferType.REGULAR;
    }

    private static long chooseBucketSeconds(Instant start, Instant end) {
        long duration = Math.max(1, end.getEpochSecond() - start.getEpochSecond());
        long[] choices = {HOUR_SECONDS, 6 * HOUR_SECONDS, 24 * HOUR_SECONDS, 7 * 24 * HOUR_SECONDS, 30 * 24 * HOUR_SECONDS};
        for (long choice : choices) {
            if ((duration + choice - 1) / choice <= 48) return choice;
        }
        return Math.max(30 * 24 * HOUR_SECONDS, (duration + 47) / 48);
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

    private boolean isSampled(String details) {
        if (details == null || details.isBlank()) return false;
        try {
            JsonNode node = objectMapper.readTree(details);
            if (node.isTextual()) node = objectMapper.readTree(node.asText());
            return node.path("trafficSampled").asBoolean(false);
        } catch (Exception ignored) {
            return false;
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

    public enum TransferDirection { UPLOAD, DOWNLOAD }
    public enum TransferType { REGULAR, MALWARE, ZIP }

    public record TrafficPoint(Instant start, long uploadedBytes, long downloadedBytes, long totalBytes) {}
    public record TrafficResponse(
            Instant start, Instant end, long bucketSeconds,
            long uploadedBytes, long downloadedBytes, long totalBytes,
            long transferCount, long peakBytes, Instant peakAt,
            List<TrafficPoint> points
    ) {}
}
