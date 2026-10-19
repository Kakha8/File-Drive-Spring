package kakha.kudava.filedrivespring.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EcsServiceHealth {
    private final String region;
    private final String cluster;
    private final String backendService;
    private final String frontendService;

    public EcsServiceHealth(@Value("${app.health.ecs.region:}") String region,
                            @Value("${app.health.ecs.cluster:}") String cluster,
                            @Value("${app.health.ecs.service:}") String backendService,
                            @Value("${app.health.ecs.web-service:}") String frontendService) {
        this.region = region;
        this.cluster = cluster;
        this.backendService = backendService;
        this.frontendService = frontendService;
    }

    public Result check() {
        if (region.isBlank() || cluster.isBlank() || backendService.isBlank() || frontendService.isBlank()) {
            return new Result("UNKNOWN", unavailable(), unavailable(), "ECS health is not configured");
        }
        try (EcsClient client = EcsClient.builder().region(Region.of(region)).build()) {
            var response = client.describeServices(DescribeServicesRequest.builder()
                    .cluster(cluster).services(backendService, frontendService).build());
            Map<String, software.amazon.awssdk.services.ecs.model.Service> services = response.services().stream()
                    .collect(Collectors.toMap(software.amazon.awssdk.services.ecs.model.Service::serviceName, Function.identity()));
            ServiceTasks backend = tasks(services.get(backendService));
            ServiceTasks frontend = tasks(services.get(frontendService));
            if (!backend.found() || !frontend.found()) {
                return new Result("DOWN", backend, frontend, "One or more ECS services were not found");
            }
            String status = serviceUp(backend) && serviceUp(frontend) ? "UP" : "DEGRADED";
            return new Result(status, backend, frontend, null);
        } catch (Exception exception) {
            return new Result("UNKNOWN", unavailable(), unavailable(), exception.getClass().getSimpleName());
        }
    }

    private static ServiceTasks tasks(software.amazon.awssdk.services.ecs.model.Service service) {
        return service == null ? unavailable() : new ServiceTasks(service.runningCount(), service.desiredCount(), true);
    }

    private static ServiceTasks unavailable() { return new ServiceTasks(0, 0, false); }
    private static boolean serviceUp(ServiceTasks service) {
        return service.found() && service.desired() > 0 && service.running() >= service.desired();
    }

    public record ServiceTasks(int running, int desired, boolean found) {}
    public record Result(String status, ServiceTasks backend, ServiceTasks frontend, String message) {}
}
