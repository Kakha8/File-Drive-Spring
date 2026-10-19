package kakha.kudava.filedrivespring.controller;

import io.micrometer.core.instrument.MeterRegistry;
import kakha.kudava.filedrivespring.health.ClamAvHealthIndicator;
import kakha.kudava.filedrivespring.health.EcsServiceHealth;
import kakha.kudava.filedrivespring.health.ObjectStorageHealthIndicator;
import kakha.kudava.filedrivespring.health.RequestHealthMetrics;
import kakha.kudava.filedrivespring.services.UploadPipelineMonitor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/health")
public class AdminHealthController {
    private final DataSource dataSource;
    private final ClamAvHealthIndicator clamAv;
    private final ObjectStorageHealthIndicator storage;
    private final EcsServiceHealth ecs;
    private final UploadPipelineMonitor uploads;
    private final MeterRegistry meters;
    private final RequestHealthMetrics requestMetrics;

    public AdminHealthController(DataSource dataSource, ClamAvHealthIndicator clamAv,
                                 ObjectStorageHealthIndicator storage, EcsServiceHealth ecs,
                                 UploadPipelineMonitor uploads, MeterRegistry meters,
                                 RequestHealthMetrics requestMetrics) {
        this.dataSource = dataSource;
        this.clamAv = clamAv;
        this.storage = storage;
        this.ecs = ecs;
        this.uploads = uploads;
        this.meters = meters;
        this.requestMetrics = requestMetrics;
    }

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        Map<String, Component> checks = new LinkedHashMap<>();
        checks.put("api", new Component("UP", Map.of()));
        checks.put("database", databaseHealth());
        checks.put("clamAv", clamHealth());
        checks.put("storage", component(storage.health()));

        EcsServiceHealth.Result ecsResult = ecs.check();
        Map<String, Object> ecsDetails = new LinkedHashMap<>();
        ecsDetails.put("runningTasks", ecsResult.backend().running() + ecsResult.frontend().running());
        ecsDetails.put("desiredTasks", ecsResult.backend().desired() + ecsResult.frontend().desired());
        ecsDetails.put("backendRunningTasks", ecsResult.backend().running());
        ecsDetails.put("backendDesiredTasks", ecsResult.backend().desired());
        ecsDetails.put("frontendRunningTasks", ecsResult.frontend().running());
        ecsDetails.put("frontendDesiredTasks", ecsResult.frontend().desired());
        ecsDetails.put("backendContainers", java.util.List.of("API", "ClamAV"));
        ecsDetails.put("frontendContainers", java.util.List.of("Web"));
        if (ecsResult.message() != null) ecsDetails.put("message", ecsResult.message());
        checks.put("ecs", new Component(ecsResult.status(), ecsDetails));

        UploadPipelineMonitor.Snapshot pipeline = uploads.snapshot();
        checks.put("uploadPipeline", new Component(pipeline.stuck() > 0 ? "DEGRADED" : "UP", Map.of(
                "active", pipeline.active(), "stuck", pipeline.stuck(), "stages", pipeline.counts())));

        RequestHealthMetrics.Snapshot http = requestMetrics.snapshot();
        checks.put("http", new Component(httpStatus(http), Map.of(
                "requestCount", http.requestCount(), "serverErrorCount", http.serverErrorCount(),
                "serverErrorRate", http.serverErrorRate(), "averageMs", http.averageMs(),
                "p50Ms", http.p50Ms(), "p95Ms", http.p95Ms(), "windowSeconds", http.windowSeconds())));

        Map<String, Object> saturation = systemSaturation();
        checks.put("system", new Component("UP", saturation));

        String overall = overallStatus(checks);
        long passing = checks.values().stream().filter(check -> "UP".equals(check.status())).count();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                new HealthResponse(overall, Instant.now(), passing, checks.size(), checks));
    }

    private Component databaseHealth() {
        long started = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(2);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("latencyMs", elapsedMs(started));
            putGauge(details, "activeConnections", "hikaricp.connections.active");
            putGauge(details, "maxConnections", "hikaricp.connections.max");
            return new Component(valid ? "UP" : "DOWN", details);
        } catch (Exception exception) {
            return new Component("DOWN", Map.of("error", exception.getClass().getSimpleName()));
        }
    }

    private static Component component(Health health) {
        return new Component(health.getStatus().getCode(), new LinkedHashMap<>(health.getDetails()));
    }

    private Component clamHealth() {
        Health health = clamAv.health();
        Map<String, Object> details = new LinkedHashMap<>(health.getDetails());
        long scans = 0;
        long failures = 0;
        double totalMs = 0;
        double maxMs = 0;
        for (var timer : meters.find("filedrive.clamav.scan").timers()) {
            scans += timer.count();
            totalMs += timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS);
            maxMs = Math.max(maxMs, timer.max(java.util.concurrent.TimeUnit.MILLISECONDS));
            if ("failure".equals(timer.getId().getTag("result"))) failures += timer.count();
        }
        details.put("scanCount", scans);
        details.put("scanFailures", failures);
        details.put("averageScanMs", scans == 0 ? 0 : totalMs / scans);
        details.put("maxScanMs", maxMs);
        return new Component(health.getStatus().getCode(), details);
    }

    private Map<String, Object> systemSaturation() {
        Map<String, Object> result = new LinkedHashMap<>();
        Double cpu = gauge("process.cpu.usage");
        Double heapUsed = gauge("jvm.memory.used", "area", "heap");
        Double heapMax = gauge("jvm.memory.max", "area", "heap");
        if (cpu != null) result.put("processCpuPercent", round(cpu * 100));
        if (heapUsed != null) result.put("heapUsedBytes", heapUsed.longValue());
        if (heapMax != null && heapMax > 0) result.put("heapMaxBytes", heapMax.longValue());
        return result;
    }

    private Double gauge(String name, String... tags) {
        try { return meters.get(name).tags(tags).gauge().value(); }
        catch (Exception ignored) { return null; }
    }

    private void putGauge(Map<String, Object> destination, String field, String meterName) {
        Double value = gauge(meterName);
        if (value != null && Double.isFinite(value)) destination.put(field, value.longValue());
    }

    private static String httpStatus(RequestHealthMetrics.Snapshot metrics) {
        if (metrics.serverErrorRate() > 0.05 || metrics.p95Ms() > 3_000) return "DOWN";
        if (metrics.serverErrorRate() > 0.01 || metrics.p95Ms() > 1_000) return "DEGRADED";
        return "UP";
    }

    private static String overallStatus(Map<String, Component> checks) {
        if (checks.values().stream().anyMatch(check -> "DOWN".equals(check.status()))) return "CRITICAL";
        if (checks.values().stream().anyMatch(check -> "DEGRADED".equals(check.status()))) return "DEGRADED";
        if (checks.values().stream().anyMatch(check -> "UNKNOWN".equals(check.status()))) return "DEGRADED";
        if (checks.values().stream().allMatch(check -> "UNKNOWN".equals(check.status()))) return "UNKNOWN";
        return "HEALTHY";
    }

    private static long elapsedMs(long started) { return (System.nanoTime() - started) / 1_000_000; }
    private static double round(double value) { return Math.round(value * 10.0) / 10.0; }
    public record Component(String status, Map<String, Object> details) {}
    public record HealthResponse(String status, Instant checkedAt, long passingChecks,
                                 long totalChecks, Map<String, Component> checks) {}
}
