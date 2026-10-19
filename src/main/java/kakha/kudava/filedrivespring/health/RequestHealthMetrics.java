package kakha.kudava.filedrivespring.health;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class RequestHealthMetrics {
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final int MAX_SAMPLES = 20_000;
    private final ConcurrentLinkedDeque<Sample> samples = new ConcurrentLinkedDeque<>();

    public void record(int status, long durationNanos) {
        samples.addLast(new Sample(Instant.now(), status, durationNanos / 1_000_000.0));
        prune();
    }

    public Snapshot snapshot() {
        prune();
        Sample[] current = samples.toArray(Sample[]::new);
        double[] durations = Arrays.stream(current).mapToDouble(Sample::durationMs).sorted().toArray();
        long errors = Arrays.stream(current).filter(sample -> sample.status() >= 500).count();
        double total = Arrays.stream(durations).sum();
        return new Snapshot(current.length, errors, current.length == 0 ? 0 : (double) errors / current.length,
                current.length == 0 ? 0 : total / current.length, percentile(durations, 0.50),
                percentile(durations, 0.95), WINDOW.toSeconds());
    }

    private void prune() {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (true) {
            Sample first = samples.peekFirst();
            if (first == null || (!first.at().isBefore(cutoff) && samples.size() <= MAX_SAMPLES)) return;
            samples.pollFirst();
        }
    }

    private static double percentile(double[] values, double percentile) {
        if (values.length == 0) return 0;
        int index = Math.min(values.length - 1, Math.max(0, (int) Math.ceil(percentile * values.length) - 1));
        return values[index];
    }

    private record Sample(Instant at, int status, double durationMs) {}
    public record Snapshot(long requestCount, long serverErrorCount, double serverErrorRate,
                           double averageMs, double p50Ms, double p95Ms, long windowSeconds) {}
}
