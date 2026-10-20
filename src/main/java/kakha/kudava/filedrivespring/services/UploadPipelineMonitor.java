package kakha.kudava.filedrivespring.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UploadPipelineMonitor {
    public enum Stage { RECEIVING, SCANNING, STORING, COMPLETED, FAILED, CANCELED, QUARANTINED }

    private final Map<String, UploadState> uploads = new ConcurrentHashMap<>();
    private final Duration stuckAfter;

    public UploadPipelineMonitor(@Value("${app.health.upload-stuck-after-minutes:15}") long stuckAfterMinutes) {
        this.stuckAfter = Duration.ofMinutes(Math.max(1, stuckAfterMinutes));
    }

    public void receiving(String uploadId, long uploadedBytes, long totalBytes) {
        if (!valid(uploadId)) return;
        uploads.compute(uploadId, (ignored, current) -> {
            Instant started = current == null ? Instant.now() : current.startedAt();
            return new UploadState(Stage.RECEIVING, started, Instant.now(),
                    Math.max(0, uploadedBytes), Math.max(0, totalBytes));
        });
    }

    public void stage(String uploadId, Stage stage) {
        if (!valid(uploadId)) return;
        uploads.compute(uploadId, (ignored, current) -> new UploadState(stage,
                current == null ? Instant.now() : current.startedAt(), Instant.now(),
                current == null ? 0 : current.uploadedBytes(), current == null ? 0 : current.totalBytes()));
    }

    public Snapshot snapshot() {
        EnumMap<Stage, Long> counts = new EnumMap<>(Stage.class);
        for (Stage stage : Stage.values()) counts.put(stage, 0L);
        Instant staleBefore = Instant.now().minus(stuckAfter);
        long stuck = 0;
        for (UploadState state : uploads.values()) {
            counts.compute(state.stage(), (ignored, value) -> value + 1);
            if (active(state.stage()) && state.updatedAt().isBefore(staleBefore)) stuck++;
        }
        long active = counts.get(Stage.RECEIVING) + counts.get(Stage.SCANNING) + counts.get(Stage.STORING);
        return new Snapshot(active, stuck, Map.copyOf(counts));
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void discardOldTerminalStates() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(24));
        uploads.entrySet().removeIf(entry -> !active(entry.getValue().stage())
                && entry.getValue().updatedAt().isBefore(cutoff));
    }

    private static boolean active(Stage stage) {
        return stage == Stage.RECEIVING || stage == Stage.SCANNING || stage == Stage.STORING;
    }

    private static boolean valid(String uploadId) {
        return uploadId != null && !uploadId.isBlank();
    }

    private record UploadState(Stage stage, Instant startedAt, Instant updatedAt,
                               long uploadedBytes, long totalBytes) {}
    public record Snapshot(long active, long stuck, Map<Stage, Long> counts) {}
}
