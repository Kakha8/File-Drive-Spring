package kakha.kudava.filedrivespring.dto;

import kakha.kudava.filedrivespring.model.LockboxFile;

import java.time.Instant;

public record LockboxFileItemResponse(
        Long id,
        String fileName,
        long ciphertextSize,
        String ciphertextChecksum,
        Instant createdAt,
        int formatVersion,
        LockboxFile.AlgorithmSuite algorithmSuite,
        int chunkSize
) {
}
