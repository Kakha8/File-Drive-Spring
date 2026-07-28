package kakha.kudava.filedrivespring.records;

import java.io.InputStream;
import java.util.Objects;

public record LockboxDownloadResult(
        String fileName,
        long ciphertextSize,
        InputStream inputStream
) {
    public LockboxDownloadResult {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException(
                    "Download file name is required."
            );
        }

        if (ciphertextSize < 0) {
            throw new IllegalArgumentException(
                    "Ciphertext size cannot be negative."
            );
        }

        Objects.requireNonNull(
                inputStream,
                "inputStream"
        );
    }
}
