package kakha.kudava.filedrivespring.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import jakarta.transaction.Transactional;
import kakha.kudava.filedrivespring.enums.EntityType;
import kakha.kudava.filedrivespring.model.FileMetaData;
import kakha.kudava.filedrivespring.model.Folders;
import kakha.kudava.filedrivespring.repository.FileMetaDataRepository;
import kakha.kudava.filedrivespring.repository.FolderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class MoveService {


    private final MinioClient minioClient;
    private final String bucket;
    private final FileMetaDataRepository fileMetaDataRepository;
    private final FolderRepository folderRepository;
    private final LogsService logsService;
    private final ObjectMapper objectMapper;

    public MoveService(MinioClient minioClient, @Value("${s3.bucket}") String bucket, FileMetaDataRepository fileMetaDataRepository,
                       FolderRepository folderRepository, LogsService logsService, ObjectMapper objectMapper) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.fileMetaDataRepository = fileMetaDataRepository;
        this.folderRepository = folderRepository;
        this.logsService = logsService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FileMetaData copyFile(Long fileId, Long targetFolderId) {
        FileMetaData fileMeta = fileMetaDataRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        Folders targetFolder = folderRepository.findById(targetFolderId)
                .orElseThrow(() -> new RuntimeException("Target folder not found"));

        String oldKey = fileMeta.getObjectKey();

        String prefix = targetFolder.getPrefix();
        if (!prefix.endsWith("/")) prefix += "/";

        String newKey = prefix + UUID.randomUUID() + "-" + fileMeta.getFileName();

        try {
            // copy
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucket)
                            .object(newKey)
                            .source(
                                    CopySource.builder()
                                            .bucket(bucket)
                                            .object(oldKey)
                                            .build()
                            )
                            .build()
            );

            FileMetaData newFile = new FileMetaData();
            newFile.setObjectKey(newKey);
            newFile.setFileName(fileMeta.getFileName());
            newFile.setSize(fileMeta.getSize());
            newFile.setDeleted(false);
            newFile.setChecksum(fileMeta.getChecksum());
            newFile.setParent(targetFolder);
            newFile.setObjectType(fileMeta.getObjectType());

/*
            fileMeta.setObjectKey(newKey);
            fileMeta.setParent(targetFolder);

            FileMetaData saved = fileMetaDataRepository.save(fileMeta);
*/


            return fileMetaDataRepository.save(newFile);

        } catch (Exception e) {
            throw new RuntimeException("Move failed", e);
        }
    }


    @Transactional
    public FileMetaData moveFile(Long fileId, Long targetFolderId) {
        FileMetaData fileMeta = fileMetaDataRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        Folders targetFolder = folderRepository.findById(targetFolderId)
                .orElseThrow(() -> new RuntimeException("Target folder not found"));

        Folders currentFolder = fileMeta.getParent();
        if (currentFolder == null) {
            throw new RuntimeException("File has no parent folder");
        }

        String oldKey = fileMeta.getObjectKey();
        String oldFolder = currentFolder.getPrefix();
        Long oldFolderId = currentFolder.getId();

        Folders oldParentFolder = currentFolder.getParent(); // may be null for root
        Long oldParentId = oldParentFolder != null ? oldParentFolder.getId() : null;

        String prefix = targetFolder.getPrefix();
        if (!prefix.endsWith("/")) prefix += "/";

        String newKey = prefix + UUID.randomUUID() + "-" + fileMeta.getFileName();

        try {
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucket)
                            .object(newKey)
                            .source(
                                    CopySource.builder()
                                            .bucket(bucket)
                                            .object(oldKey)
                                            .build()
                            )
                            .build()
            );

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(oldKey)
                            .build()
            );

            fileMeta.setObjectKey(newKey);
            fileMeta.setParent(targetFolder);

            Map<String, Object> detailsMap = new LinkedHashMap<>();            detailsMap.put("oldFolder", oldFolder);
            detailsMap.put("oldFolderId", oldFolderId);
            detailsMap.put("targetFolder", targetFolder.getPrefix());
            detailsMap.put("targetFolderId", targetFolder.getId());

            FileMetaData saved = fileMetaDataRepository.save(fileMeta);

            String detailsJson = objectMapper.writeValueAsString(detailsMap);

            logsService.moveLog(
                    fileMeta.getFileName(),
                    fileMeta.getId(),
                    "FILE",
                    detailsJson
            );

            return saved;

        } catch (Exception e) {
            throw new RuntimeException("Move failed", e);
        }
    }
}
