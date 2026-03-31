package kakha.kudava.filedrivespring.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.*;
import io.minio.messages.Item;
import jakarta.transaction.Transactional;
import kakha.kudava.filedrivespring.enums.EntityType;
import kakha.kudava.filedrivespring.model.FileMetaData;
import kakha.kudava.filedrivespring.model.Folders;
import kakha.kudava.filedrivespring.repository.FileMetaDataRepository;
import kakha.kudava.filedrivespring.repository.FolderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

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


            Map<String, Object> detailsMap = new LinkedHashMap<>();

            detailsMap.put("targetFolder", targetFolder.getPrefix());
            detailsMap.put("targetFolderId", targetFolder.getId());

            String detailsJson = objectMapper.writeValueAsString(detailsMap);

            logsService.copyLog(
                    fileMeta.getFileName(),
                    fileMeta.getId(),
                    "FILE",
                    detailsJson
            );

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

            Map<String, Object> detailsMap = new LinkedHashMap<>();
            detailsMap.put("oldFolder", oldFolder);
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

    @Transactional
    public void moveFolder(Long folderId, Long targetFolderId) {
        Folders folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        Folders target = folderRepository.findById(targetFolderId)
                .orElseThrow(() -> new RuntimeException("Target folder not found"));

        String oldPrefix = normalize(folder.getPrefix());
        String newPrefix = normalize(target.getPrefix()) + folder.getName() + "/";

        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(oldPrefix)
                            .recursive(true)
                            .build()
            );

            for (Result<Item> r : objects) {
                Item item = r.get();
                String oldKey = item.objectName();

                String newKey = newPrefix + oldKey.substring(oldPrefix.length());

                // copy
                minioClient.copyObject(
                        CopyObjectArgs.builder()
                                .bucket(bucket)
                                .object(newKey)
                                .source(CopySource.builder()
                                        .bucket(bucket)
                                        .object(oldKey)
                                        .build())
                                .build()
                );

                // delete old
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucket)
                                .object(oldKey)
                                .build()
                );

                fileMetaDataRepository.findByObjectKey(oldKey)
                        .ifPresent(file -> {
                            file.setObjectKey(newKey);
                            fileMetaDataRepository.save(file);
                        });
            }

            updateFolderPrefixes(folder, oldPrefix, newPrefix, target);

        } catch (Exception e) {
            throw new RuntimeException("Folder move failed", e);
        }
    }

    private void updateFolderPrefixes(Folders folder,
                                      String oldPrefix,
                                      String newPrefix,
                                      Folders newParent) {

        // update current folder
        String updatedPrefix = folder.getPrefix().replaceFirst(oldPrefix, newPrefix);
        folder.setPrefix(updatedPrefix);
        folder.setParent(newParent);
        folderRepository.save(folder);

        // update children recursively
        if (folder.getChildren() != null) {
            for (Folders child : folder.getChildren()) {
                updateFolderPrefixes(child, oldPrefix, newPrefix, folder);
            }
        }
    }

    @Transactional
    public Folders copyFolder(Long folderId, Long targetFolderId) {
        Folders sourceFolder = folderRepository.findById(folderId)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        Folders targetFolder = folderRepository.findById(targetFolderId)
                .orElseThrow(() -> new RuntimeException("Target folder not found"));

        String targetPrefix = normalizePrefix(targetFolder.getPrefix());
        String newRootPrefix = targetPrefix + sourceFolder.getName() + "/";

        try {
            Folders copiedRoot = copyFolderRecursive(sourceFolder, targetFolder, newRootPrefix);

            Map<String, Object> detailsMap = new LinkedHashMap<>();
            detailsMap.put("targetFolder", targetFolder.getPrefix());
            detailsMap.put("targetFolderId", targetFolder.getId());

            String detailsJson = objectMapper.writeValueAsString(detailsMap);

            logsService.copyLog(
                    sourceFolder.getName(),
                    sourceFolder.getId(),
                    "FOLDER",
                    detailsJson
            );

            return copiedRoot;

        } catch (Exception e) {
            throw new RuntimeException("Folder copy failed", e);
        }
    }

    private Folders copyFolderRecursive(Folders sourceFolder, Folders newParent, String newPrefix) throws Exception {
        // 1. create copied folder entity
        Folders copiedFolder = new Folders();
        copiedFolder.setName(sourceFolder.getName());
        copiedFolder.setPrefix(newPrefix);
        copiedFolder.setDeleted(false);
        copiedFolder.setParent(newParent);

        copiedFolder = folderRepository.save(copiedFolder);

        // 2. copy files that belong directly to this folder
        List<FileMetaData> files = fileMetaDataRepository.findAllByParentId(sourceFolder.getId());

        for (FileMetaData sourceFile : files) {
            String oldKey = sourceFile.getObjectKey();
            String newKey = newPrefix + UUID.randomUUID() + "-" + sourceFile.getFileName();

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

            FileMetaData copiedFile = new FileMetaData();
            copiedFile.setObjectKey(newKey);
            copiedFile.setFileName(sourceFile.getFileName());
            copiedFile.setSize(sourceFile.getSize());
            copiedFile.setDeleted(false);
            copiedFile.setChecksum(sourceFile.getChecksum());
            copiedFile.setParent(copiedFolder);
            copiedFile.setObjectType(sourceFile.getObjectType());

            fileMetaDataRepository.save(copiedFile);

            Map<String, Object> fileDetailsMap = new LinkedHashMap<>();
            fileDetailsMap.put("targetFolder", copiedFolder.getPrefix());
            fileDetailsMap.put("targetFolderId", copiedFolder.getId());

            String fileDetailsJson = objectMapper.writeValueAsString(fileDetailsMap);

            logsService.copyLog(
                    sourceFile.getFileName(),
                    sourceFile.getId(),
                    "FILE",
                    fileDetailsJson
            );
        }

        // 3. recursively copy child folders
        if (sourceFolder.getChildren() != null) {
            for (Folders child : sourceFolder.getChildren()) {
                String childPrefix = normalizePrefix(newPrefix) + child.getName() + "/";
                copyFolderRecursive(child, copiedFolder, childPrefix);
            }
        }

        return copiedFolder;
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }
    private String normalize(String prefix) {
        if (!prefix.endsWith("/")) return prefix + "/";
        return prefix;
    }
}
