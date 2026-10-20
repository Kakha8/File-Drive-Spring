package kakha.kudava.filedrivespring.health;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("objectStorage")
public class ObjectStorageHealthIndicator implements HealthIndicator {
    private final MinioClient client;
    private final String bucket;

    public ObjectStorageHealthIndicator(MinioClient client, @Value("${s3.bucket}") String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public Health health() {
        long started = System.nanoTime();
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            long latencyMs = (System.nanoTime() - started) / 1_000_000;
            return exists ? Health.up().withDetail("latencyMs", latencyMs).build()
                    : Health.down().withDetail("reason", "Storage bucket is unavailable").build();
        } catch (Exception exception) {
            return Health.down().withDetail("error", exception.getClass().getSimpleName()).build();
        }
    }
}
