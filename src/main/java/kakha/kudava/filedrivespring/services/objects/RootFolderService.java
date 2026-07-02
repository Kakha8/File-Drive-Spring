package kakha.kudava.filedrivespring.services.objects;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.transaction.Transactional;
import kakha.kudava.filedrivespring.model.Folders;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.FolderRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.XSlf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

@Slf4j
@Service
public class RootFolderService {

    private final FolderRepository folderRepository;
    private final MinioClient minioClient;
    private final String bucket;
    private final String trashBucket;

    public RootFolderService(
            FolderRepository folderRepository,
            MinioClient minioClient,
            @Value("${s3.bucket}") String bucket,
            @Value("${s3.trash-bucket}") String trashBucket
    ) {
        this.folderRepository = folderRepository;
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.trashBucket = trashBucket;
    }

    @Transactional
    public Folders ensureRootFolder(User user) {
        return folderRepository
                .findByOwnerAndParentIsNullAndDeletedFalseAndPermanentlyDeletedFalse(user)
                .orElseGet(() -> {
                    Folders root = new Folders();
                    root.setName("My Drive");
                    root.setPrefix("users/" + user.getId() + "/");
                    root.setOwner(user);
                    root.setParent(null);
                    root.setDeleted(false);
                    root.setPermanentlyDeleted(false);

                    return folderRepository.save(root);
                });
    }

    private Folders createRootFolder(User user) {
        String rootPrefix = "users/" + user.getId() + "/";

        Folders root = new Folders();
        root.setName("root");
        root.setPrefix(rootPrefix);
        root.setOwner(user);
        root.setParent(null);
        root.setDeleted(false);
        root.setPermanentlyDeleted(false);

        Folders saved = folderRepository.save(root);

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(rootPrefix)
                            .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                            .contentType("application/x-directory")
                            .build()
            );
        } catch (Exception e) {
            log.warn("Root folder DB row was created, but MinIO prefix placeholder failed: {}", rootPrefix, e);
        }

        return saved;
    }

    private String rootPrefix(User user) {
        return "users/" + user.getId() + "/";
    }
}