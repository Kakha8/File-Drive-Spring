package kakha.kudava.filedrivespring.config;

import io.minio.MinioClient;
import io.minio.credentials.IamAwsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {


    @Bean
    public MinioClient minioClient(@Value("${s3.endpoint}") String endpoint,
                                   @Value("${s3.region:}") String region,
                                   @Value("${s3.use-iam-role:false}") boolean useIamRole,
                                   @Value("${s3.access-key:}") String accessKey,
                                   @Value("${s3.secret-key:}") String secretKey) {
        MinioClient.Builder builder = MinioClient.builder().endpoint(endpoint);

        if (!region.isBlank()) {
            builder.region(region);
        }

        if (useIamRole) {
            builder.credentialsProvider(new IamAwsProvider(null, null));
        } else {
            builder.credentials(accessKey, secretKey);
        }

        return builder.build();
    }

}
