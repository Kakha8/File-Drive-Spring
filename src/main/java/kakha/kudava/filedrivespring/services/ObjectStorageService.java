package kakha.kudava.filedrivespring.services;

import io.minio.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import kakha.kudava.filedrivespring.dto.FileMetaDataDTO;
import kakha.kudava.filedrivespring.dto.UserDTO;
import kakha.kudava.filedrivespring.model.FileMetaData;
import kakha.kudava.filedrivespring.model.Folders;
import kakha.kudava.filedrivespring.repository.FileMetaDataRepository;
import kakha.kudava.filedrivespring.repository.FolderRepository;
import kakha.kudava.filedrivespring.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.apache.sshd.common.util.buffer.BufferUtils.toHex;

@Slf4j
@Service
public class ObjectStorageService {

    private final MinioClient minioClient;
    private final String bucket;
    private final FileMetaDataRepository fileMetaDataRepository;

    private final FolderRepository folderRepository;
    private final LogsService logsService;

    public ObjectStorageService(MinioClient minioClient, @Value("${s3.bucket}") String bucket, FileMetaDataRepository fileMetaDataRepository, FolderRepository folderRepository, LogsService logsService) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.fileMetaDataRepository = fileMetaDataRepository;
        this.folderRepository = folderRepository;
        this.logsService = logsService;
    }

    public FileMetaData upload(MultipartFile file, Long parentId) throws Exception {

        MessageDigest md = MessageDigest.getInstance("SHA-256");

        Folders folder = folderRepository.findById(parentId)
                .orElseThrow(() -> new RuntimeException("Folder not found: " + parentId));

        String safeName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String prefix = folder.getPrefix();
        if (!prefix.endsWith("/")) prefix += "/";
        String objectKey = prefix + UUID.randomUUID() + "-" + safeName;

        try(InputStream in = file.getInputStream();
            DigestInputStream dis = new DigestInputStream(in, md)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(dis, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            byte[] hash = md.digest();
            String checksum = toHex(hash);
            Long fileSize = file.getSize();

            FileMetaData entity = new FileMetaData();
            entity.setDeleted(false);
            entity.setObjectKey(objectKey);
            entity.setObjectType(file.getContentType());
            entity.setFileName(file.getOriginalFilename());
            entity.setChecksum(checksum);
            entity.setSize(fileSize);
            entity.setParent(folder);

            log.info("File uploaded successfully {}", objectKey);
            logsService.uploadLog(file.getName(), parentId, "FILE");

            return fileMetaDataRepository.save(entity);

        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public FileMetaData getMeta(Long id) {
        return fileMetaDataRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Object not found"));
    }

    public record UploadResult(String objectKey, String checksum, Long fileSize) {}



    public InputStream download(Long id) throws Exception {
        FileMetaData fileMetaData = fileMetaDataRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Object not found"));
        String objectKey = fileMetaData.getObjectKey();

        log.info("Downloading object from {}", objectKey);

        logsService.downloadLog(objectKey, id, "FILE");
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build()
        );
    }

    public void delete(Long id){

        FileMetaData metaData = fileMetaDataRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Object not found"));
        String objectKey = metaData.getObjectKey();

        metaData.setDeleted(true);
        fileMetaDataRepository.save(metaData);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
            log.info("Object deleted successfully {}", objectKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete object: " + objectKey, e);
        }
    }

    public void deleteByPrefix(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("prefix is null");
        }

        String p = prefix.trim().replace("\\", "/");
        if (p.isEmpty()) {
            throw new IllegalArgumentException("prefix is empty");
        }

        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(p)
                            .recursive(true)   // include nested folders
                            .build()
            );

            // Batch deletes so we don't keep everything in memory.
            final int BATCH_SIZE = 1000;
            List<DeleteObject> batch = new ArrayList<>(BATCH_SIZE);

            for (Result<Item> r : results) {
                Item item = r.get();

                log.debug("Deleting object from bucket: {}", item.objectName());
                batch.add(new DeleteObject(item.objectName()));

                if (batch.size() >= BATCH_SIZE) {
                    deleteBatch(batch);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                deleteBatch(batch);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete objects by prefix: " + p, e);
        }
    }

    private void deleteBatch(List<DeleteObject> objects) throws Exception {
        Iterable<Result<DeleteError>> errors = minioClient.removeObjects(
                RemoveObjectsArgs.builder()
                        .bucket(bucket)
                        .objects(objects)
                        .build()
        );

        for (Result<DeleteError> r : errors) {
            DeleteError err = r.get();

            throw new RuntimeException(
                    "MinIO delete failed for object=" + err.objectName()
                            + " message=" + err.message()
                            + " code=" + err.code()
            );
        }
    }

}
