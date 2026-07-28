package kakha.kudava.filedrivespring.records;

import kakha.kudava.filedrivespring.model.LockboxFile;

import java.util.Objects;

public record LockboxContainerInfo(
        int formatVersion,
        LockboxFile.AlgorithmSuite algorithmSuite,
        int chunkSize,
        long plaintextLength,
        long totalChunks,
        int kemCiphertextLength,
        int wrappedDekLength,
        long containerSize
) {

    public LockboxContainerInfo {
        if (formatVersion < 1) {
            throw new IllegalArgumentException(
                    "Format version must be positive."
            );
        }

        Objects.requireNonNull(
                algorithmSuite,
                "algorithmSuite"
        );

        if (chunkSize < 1) {
            throw new IllegalArgumentException(
                    "Chunk size must be positive."
            );
        }

        if (plaintextLength < 0) {
            throw new IllegalArgumentException(
                    "Plaintext length cannot be negative."
            );
        }

        if (totalChunks < 1) {
            throw new IllegalArgumentException(
                    "Total chunks must be positive."
            );
        }

        if (kemCiphertextLength < 1) {
            throw new IllegalArgumentException(
                    "KEM ciphertext length must be positive."
            );
        }

        if (wrappedDekLength < 16) {
            throw new IllegalArgumentException(
                    "Wrapped DEK length is invalid."
            );
        }

        if (containerSize < 1) {
            throw new IllegalArgumentException(
                    "Container size must be positive."
            );
        }
    }
}