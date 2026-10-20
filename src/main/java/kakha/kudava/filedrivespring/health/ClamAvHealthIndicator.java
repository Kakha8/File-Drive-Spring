package kakha.kudava.filedrivespring.health;

import kakha.kudava.filedrivespring.services.ClamAvScannerService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("clamAv")
public class ClamAvHealthIndicator implements HealthIndicator {
    private final ClamAvScannerService scanner;

    public ClamAvHealthIndicator(ClamAvScannerService scanner) {
        this.scanner = scanner;
    }

    @Override
    public Health health() {
        try {
            var probe = scanner.probe();
            return Health.up().withDetail("version", probe.version())
                    .withDetail("latencyMs", probe.latencyMs()).build();
        } catch (Exception exception) {
            return Health.down().withDetail("error", exception.getClass().getSimpleName()).build();
        }
    }
}
