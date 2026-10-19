package kakha.kudava.filedrivespring.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;

@Service
public class EcsServiceHealth {
    private final String region;
    private final String cluster;
    private final String service;

    public EcsServiceHealth(@Value("${app.health.ecs.region:}") String region,
                            @Value("${app.health.ecs.cluster:}") String cluster,
                            @Value("${app.health.ecs.service:}") String service) {
        this.region = region;
        this.cluster = cluster;
        this.service = service;
    }

    public Result check() {
        if (region.isBlank() || cluster.isBlank() || service.isBlank()) {
            return new Result("UNKNOWN", 0, 0, "ECS health is not configured");
        }
        try (EcsClient client = EcsClient.builder().region(Region.of(region)).build()) {
            var response = client.describeServices(DescribeServicesRequest.builder()
                    .cluster(cluster).services(service).build());
            if (response.services().isEmpty()) return new Result("DOWN", 0, 0, "Service not found");
            var state = response.services().getFirst();
            int desired = state.desiredCount();
            int running = state.runningCount();
            return new Result(running >= desired && desired > 0 ? "UP" : "DEGRADED", running, desired, null);
        } catch (Exception exception) {
            return new Result("UNKNOWN", 0, 0, exception.getClass().getSimpleName());
        }
    }

    public record Result(String status, int running, int desired, String message) {}
}
