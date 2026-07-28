package kakha.kudava.filedrivespring.dto;

import kakha.kudava.filedrivespring.model.LockboxFile;

import java.time.Instant;

public record LockboxUploadResponse(
        Long id,
        String fileName,
        Long parentId,
        long ciphertextSize,
        String ciphertextChecksum,
        int formatVersion,
        LockboxFile.AlgorithmSuite algorithmSuite,
        int chunkSize,
        Instant createdAt
) {
}