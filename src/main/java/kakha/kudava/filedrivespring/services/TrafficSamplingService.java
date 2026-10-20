package kakha.kudava.filedrivespring.services;

import kakha.kudava.filedrivespring.model.TrafficSample;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.TrafficSampleRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class TrafficSamplingService {
    private static final long FLUSH_INTERVAL_MILLIS = 1_000;
    private final TrafficSampleRepository repository;
    private final Clock clock;

    @Autowired
    public TrafficSamplingService(TrafficSampleRepository repository) {
        this(repository, Clock.systemUTC());
    }

    TrafficSamplingService(TrafficSampleRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Tracker startDownload(User user, String transferType) {
        return new Tracker(UUID.randomUUID().toString(), user, transferType);
    }

    /**
     * Records browser upload progress as a cumulative byte count. Persisting only
     * the difference makes repeated progress reports and authenticated retries
     * idempotent.
     */
    public synchronized void reportUploadProgress(User user, String transferId, long uploadedBytes) {
        if (transferId == null || transferId.isBlank() || uploadedBytes <= 0) return;
        long recorded = recordedBytes(transferId, user);
        saveUploadDelta(user, transferId, "REGULAR", uploadedBytes - recorded);
    }

    /** Reconciles progress with the authoritative file size once processing completes. */
    public synchronized void completeUpload(User user, String transferId, long fileSize, String transferType) {
        if (transferId == null || transferId.isBlank() || fileSize < 0) return;
        var existing = repository.findByTransferIdAndUser(transferId, user);
        String normalizedType = transferType == null || transferType.isBlank() ? "REGULAR" : transferType;
        boolean changed = false;
        long recorded = 0;
        for (TrafficSample sample : existing) {
            recorded = saturatedAdd(recorded, Math.max(0, sample.getBytes()));
            if (!normalizedType.equals(sample.getTransferType())) {
                sample.setTransferType(normalizedType);
                changed = true;
            }
        }
        if (changed) repository.saveAll(existing);
        saveUploadDelta(user, transferId, normalizedType, fileSize - recorded);
    }

    private long recordedBytes(String transferId, User user) {
        long total = 0;
        for (TrafficSample sample : repository.findByTransferIdAndUser(transferId, user)) {
            total = saturatedAdd(total, Math.max(0, sample.getBytes()));
        }
        return total;
    }

    private void saveUploadDelta(User user, String transferId, String transferType, long bytes) {
        if (bytes <= 0) return;
        TrafficSample sample = new TrafficSample();
        sample.setUser(user);
        sample.setTransferId(transferId);
        sample.setDirection("UPLOAD");
        sample.setTransferType(transferType);
        sample.setBytes(bytes);
        sample.setOccurredAt(clock.instant());
        repository.save(sample);
    }

    public final class Tracker {
        private final String transferId;
        private final User user;
        private final String transferType;
        private long pendingBytes;
        private long lastFlushMillis;
        private boolean closed;

        private Tracker(String transferId, User user, String transferType) {
            this.transferId = transferId;
            this.user = user;
            this.transferType = transferType;
            this.lastFlushMillis = clock.millis();
        }

        public synchronized void record(long bytes) {
            if (closed || bytes <= 0) return;
            pendingBytes = saturatedAdd(pendingBytes, bytes);
            long now = clock.millis();
            if (now - lastFlushMillis >= FLUSH_INTERVAL_MILLIS) flush(Instant.ofEpochMilli(now));
        }

        public synchronized void close() {
            if (closed) return;
            flush(clock.instant());
            closed = true;
        }

        public String transferId() {
            return transferId;
        }

        private void flush(Instant occurredAt) {
            if (pendingBytes <= 0) {
                lastFlushMillis = occurredAt.toEpochMilli();
                return;
            }
            TrafficSample sample = new TrafficSample();
            sample.setUser(user);
            sample.setTransferId(transferId);
            sample.setDirection("DOWNLOAD");
            sample.setTransferType(transferType);
            sample.setBytes(pendingBytes);
            sample.setOccurredAt(occurredAt);
            repository.save(sample);
            pendingBytes = 0;
            lastFlushMillis = occurredAt.toEpochMilli();
        }
    }

    private static long saturatedAdd(long left, long right) {
        return right > 0 && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
