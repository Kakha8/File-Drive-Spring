package kakha.kudava.filedrivespring.services;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import kakha.kudava.filedrivespring.dto.FileMetaDataDTO;
import kakha.kudava.filedrivespring.dto.UserDTO;
import kakha.kudava.filedrivespring.model.FileMetaData;
import kakha.kudava.filedrivespring.repository.FileMetaDataRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.UUID;

import static org.apache.sshd.common.util.buffer.BufferUtils.toHex;

@Service
public class ObjectStorageService {

    private final MinioClient minioClient;
    private final String bucket;
    private final FileMetaDataRepository fileMetaDataRepository;

    public ObjectStorageService(MinioClient minioClient, @Value("${s3.bucket}") String bucket, FileMetaDataRepository fileMetaDataRepository) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.fileMetaDataRepository = fileMetaDataRepository;
    }

    public UploadResult upload(MultipartFile file) throws Exception {

        MessageDigest md = MessageDigest.getInstance("SHA-256");


        String safeName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String objectKey = UUID.randomUUID() + "-" + safeName;
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

            FileMetaData entity = new FileMetaData();
            entity.setDeleted(false);
            entity.setObjectKey(objectKey);
            entity.setObjectType(file.getContentType());
            entity.setFileName(file.getOriginalFilename());
            entity.setChecksum(checksum);
            fileMetaDataRepository.save(entity);

            return new UploadResult(objectKey, checksum);

        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public record UploadResult(String objectKey, String checksum) {}
    public InputStream download(String objectKey) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build()
        );
    }

    public void delete(String objectKey){

        FileMetaData metaData = fileMetaDataRepository.findByObjectKey(objectKey)
                .orElseThrow(() -> new RuntimeException("Object not found"));

        metaData.setDeleted(true);
        fileMetaDataRepository.save(metaData);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete object: " + objectKey, e);
        }
    }
}
