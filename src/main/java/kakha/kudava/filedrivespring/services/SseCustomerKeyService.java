package kakha.kudava.filedrivespring.services;

import io.minio.ServerSideEncryption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import io.minio.ServerSideEncryptionCustomerKey;

@Service
public class SseCustomerKeyService {

    private static final String KEYSTORE_TYPE = "JCEKS";

    private final boolean enabled;
    private final Path keystorePath;
    private final String keyAlias;
    private final char[] keystorePassword;
    private final char[] keyPassword;

    public SseCustomerKeyService(
            @Value("${s3.encryption.ssec.enabled:false}") boolean enabled,
            @Value("${s3.encryption.keystore.path:./keys/minio-sse-c.jceks}") String keystorePath,
            @Value("${s3.encryption.keystore.alias:minio-sse-c-key}") String keyAlias,
            @Value("${s3.encryption.keystore.password:changeit-keystore}") String keystorePassword,
            @Value("${s3.encryption.key.password:changeit-key}") String keyPassword
    ) {
        this.enabled = enabled;
        this.keystorePath = Path.of(keystorePath);
        this.keyAlias = keyAlias;
        this.keystorePassword = keystorePassword.toCharArray();
        this.keyPassword = keyPassword.toCharArray();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ServerSideEncryptionCustomerKey customerKey() throws Exception {
        if (!enabled) {
            return null;
        }

        return new ServerSideEncryptionCustomerKey(loadOrCreateAesKey());
    }

    private SecretKey loadOrCreateAesKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);

        if (Files.exists(keystorePath)) {
            try (InputStream inputStream = Files.newInputStream(keystorePath)) {
                keyStore.load(inputStream, keystorePassword);
            }

            KeyStore.Entry entry = keyStore.getEntry(
                    keyAlias,
                    new KeyStore.PasswordProtection(keyPassword)
            );

            if (entry instanceof KeyStore.SecretKeyEntry secretKeyEntry) {
                return secretKeyEntry.getSecretKey();
            }

            throw new IllegalStateException("Keystore alias is not a secret key: " + keyAlias);
        }

        keyStore.load(null, keystorePassword);

        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256, SecureRandom.getInstanceStrong());

        SecretKey secretKey = keyGenerator.generateKey();

        keyStore.setEntry(
                keyAlias,
                new KeyStore.SecretKeyEntry(secretKey),
                new KeyStore.PasswordProtection(keyPassword)
        );

        Path parent = keystorePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (OutputStream outputStream = Files.newOutputStream(keystorePath)) {
            keyStore.store(outputStream, keystorePassword);
        }

        return secretKey;
    }
}