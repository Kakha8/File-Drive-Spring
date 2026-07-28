package kakha.kudava.filedrivespring.services.lockbox;

import kakha.kudava.filedrivespring.model.LockboxFile;
import kakha.kudava.filedrivespring.records.LockboxContainerInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;

@Component
public class LockboxContainerValidator {

    /*
     * Current Rust container layout:
     *
     * 0  - 7   magic: CSEMLK02
     * 8  - 11  chunk size, little-endian u32
     * 12 - 19  plaintext length, little-endian u64
     * 20 - 23  total chunks, little-endian u32
     * 24 - 27  ML-KEM ciphertext length, little-endian u32
     * 28 - 31  wrapped DEK length, little-endian u32
     * 32 - 43  wrapped-DEK nonce
     * 44 - 51  file nonce prefix
     */
    private static final int HEADER_SIZE = 52;

    private static final byte[] MAGIC = {
            'C', 'S', 'E', 'M', 'L', 'K', '0', '2'
    };

    private static final int FORMAT_VERSION = 2;

    /*
     * Your current Rust client encrypts using 1 MiB plaintext chunks.
     */
    private static final int EXPECTED_CHUNK_SIZE = 1_048_576;

    /*
     * Each AES-GCM encrypted chunk adds a 16-byte authentication tag.
     */
    private static final int GCM_TAG_SIZE = 16;

    /*
     * Defensive limits for the public cryptographic fields.
     *
     * These prevent a forged header from declaring enormous sections.
     */
    private static final int MAX_CRYPTO_FIELD_SIZE = 65_536;
    private static final int MIN_WRAPPED_DEK_SIZE = GCM_TAG_SIZE;

    private final long maxContainerSizeBytes;

    public LockboxContainerValidator(
            @Value(
                    "${lockbox.max-container-size-bytes:107374182400}"
            )
            long maxContainerSizeBytes
    ) {
        if (maxContainerSizeBytes < HEADER_SIZE) {
            throw new IllegalArgumentException(
                    "Maximum Lockbox container size is too small."
            );
        }

        this.maxContainerSizeBytes = maxContainerSizeBytes;
    }

    /**
     * Validates the public structure of a CSEMLK02 container.
     *
     * This does not decrypt the file and cannot validate the inner
     * AES-GCM tags because the server does not possess the DEK.
     */
    public LockboxContainerInfo validate(Path path) throws IOException {
        Objects.requireNonNull(path, "path");

        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw invalid("Lockbox container does not exist.");
        }

        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw invalid(
                    "Lockbox container must be a regular file."
            );
        }

        try (FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.READ
        )) {
            long actualContainerSize = channel.size();

            validateContainerSize(actualContainerSize);

            ByteBuffer header = ByteBuffer
                    .allocate(HEADER_SIZE)
                    .order(ByteOrder.LITTLE_ENDIAN);

            readEntireHeader(channel, header);
            header.flip();

            byte[] magic = new byte[MAGIC.length];
            header.get(magic);

            if (!Arrays.equals(magic, MAGIC)) {
                throw invalid(
                        "Unsupported Lockbox magic bytes. "
                                + "Expected CSEMLK02."
                );
            }

            long unsignedChunkSize =
                    Integer.toUnsignedLong(header.getInt());

            long plaintextLength = header.getLong();

            long totalChunks =
                    Integer.toUnsignedLong(header.getInt());

            long kemCiphertextLength =
                    Integer.toUnsignedLong(header.getInt());

            long wrappedDekLength =
                    Integer.toUnsignedLong(header.getInt());

            /*
             * The nonce fields are part of the public header, but the
             * server does not need their values for structural validation.
             */
            header.position(header.position() + 12);
            header.position(header.position() + 8);

            validateChunkSize(unsignedChunkSize);
            validatePlaintextLength(plaintextLength);
            validateChunkCount(
                    plaintextLength,
                    unsignedChunkSize,
                    totalChunks
            );
            validateCryptoFieldLengths(
                    kemCiphertextLength,
                    wrappedDekLength
            );

            long expectedContainerSize = calculateExpectedSize(
                    plaintextLength,
                    totalChunks,
                    kemCiphertextLength,
                    wrappedDekLength
            );

            if (actualContainerSize != expectedContainerSize) {
                throw invalid(
                        "Lockbox container size does not match its "
                                + "declared header. Expected "
                                + expectedContainerSize
                                + " bytes but received "
                                + actualContainerSize
                                + " bytes."
                );
            }

            return new LockboxContainerInfo(
                    FORMAT_VERSION,
                    LockboxFile.AlgorithmSuite
                            .ML_KEM_1024_AES_256_GCM_V1,
                    Math.toIntExact(unsignedChunkSize),
                    plaintextLength,
                    totalChunks,
                    Math.toIntExact(kemCiphertextLength),
                    Math.toIntExact(wrappedDekLength),
                    actualContainerSize
            );
        }
    }

    private void validateContainerSize(long containerSize) {
        if (containerSize < HEADER_SIZE) {
            throw invalid(
                    "Lockbox container is truncated. "
                            + "The header requires "
                            + HEADER_SIZE
                            + " bytes."
            );
        }

        if (containerSize > maxContainerSizeBytes) {
            throw invalid(
                    "Lockbox container exceeds the maximum allowed size."
            );
        }
    }

    private void readEntireHeader(
            FileChannel channel,
            ByteBuffer header
    ) throws IOException {
        while (header.hasRemaining()) {
            int bytesRead = channel.read(header);

            if (bytesRead < 0) {
                throw invalid(
                        "Lockbox container ended before the header "
                                + "was complete."
                );
            }
        }
    }

    private void validateChunkSize(long chunkSize) {
        if (chunkSize != EXPECTED_CHUNK_SIZE) {
            throw invalid(
                    "Unsupported Lockbox chunk size: "
                            + chunkSize
                            + ". Expected "
                            + EXPECTED_CHUNK_SIZE
                            + "."
            );
        }
    }

    private void validatePlaintextLength(long plaintextLength) {
        /*
         * Java does not have an unsigned long primitive. A Rust u64
         * greater than Long.MAX_VALUE appears negative here and must
         * therefore be rejected.
         */
        if (plaintextLength < 0) {
            throw invalid(
                    "Plaintext length exceeds the supported range."
            );
        }
    }

    private void validateChunkCount(
            long plaintextLength,
            long chunkSize,
            long declaredTotalChunks
    ) {
        long expectedTotalChunks;

        if (plaintextLength == 0) {
            /*
             * The current format still emits one authenticated chunk
             * for an empty plaintext file.
             */
            expectedTotalChunks = 1;
        } else {
            expectedTotalChunks =
                    ((plaintextLength - 1) / chunkSize) + 1;
        }

        if (declaredTotalChunks != expectedTotalChunks) {
            throw invalid(
                    "Invalid Lockbox chunk count. Expected "
                            + expectedTotalChunks
                            + " but the header declares "
                            + declaredTotalChunks
                            + "."
            );
        }
    }

    private void validateCryptoFieldLengths(
            long kemCiphertextLength,
            long wrappedDekLength
    ) {
        if (kemCiphertextLength < 1
                || kemCiphertextLength > MAX_CRYPTO_FIELD_SIZE) {
            throw invalid(
                    "Invalid ML-KEM ciphertext length: "
                            + kemCiphertextLength
                            + "."
            );
        }

        if (wrappedDekLength < MIN_WRAPPED_DEK_SIZE
                || wrappedDekLength > MAX_CRYPTO_FIELD_SIZE) {
            throw invalid(
                    "Invalid wrapped DEK length: "
                            + wrappedDekLength
                            + "."
            );
        }
    }

    private long calculateExpectedSize(
            long plaintextLength,
            long totalChunks,
            long kemCiphertextLength,
            long wrappedDekLength
    ) {
        try {
            long gcmTagBytes = Math.multiplyExact(
                    totalChunks,
                    GCM_TAG_SIZE
            );

            long expectedSize = HEADER_SIZE;

            expectedSize = Math.addExact(
                    expectedSize,
                    kemCiphertextLength
            );

            expectedSize = Math.addExact(
                    expectedSize,
                    wrappedDekLength
            );

            expectedSize = Math.addExact(
                    expectedSize,
                    plaintextLength
            );

            expectedSize = Math.addExact(
                    expectedSize,
                    gcmTagBytes
            );

            if (expectedSize > maxContainerSizeBytes) {
                throw invalid(
                        "Declared Lockbox container size exceeds "
                                + "the maximum allowed size."
                );
            }

            return expectedSize;
        } catch (ArithmeticException exception) {
            throw invalid(
                    "Lockbox header contains overflowing length values.",
                    exception
            );
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private IllegalArgumentException invalid(
            String message,
            Throwable cause
    ) {
        return new IllegalArgumentException(message, cause);
    }
}