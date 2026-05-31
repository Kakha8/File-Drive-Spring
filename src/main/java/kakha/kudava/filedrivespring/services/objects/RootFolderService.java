package kakha.kudava.filedrivespring.services.objects;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.transaction.Transactional;
import kakha.kudava.filedrivespring.model.Folders;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.FolderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

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
        return folderRepository.findByOwnerAndParentIsNullAndDeletedFalse(user)
                .orElseGet(() -> createRootFolder(user));
    }

    private Folders createRootFolder(User user) {
        if (user.getId() == null) {
            throw new IllegalStateException("User must be saved before creating root folder");
        }

        String prefix = rootPrefix(user);

        Folders root = new Folders();
        root.setName(user.getUsername());
        root.setPrefix(prefix);
        root.setOwner(user);
        root.setParent(null);
        root.setDeleted(false);

        Folders saved = folderRepository.save(root);

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(prefix)
                            .stream(new ByteArrayInputStream(new byte[]{}), 0, -1)
                            .contentType("application/x-directory")
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create root folder prefix: " + prefix, e);
        }

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(trashBucket)
                            .object(prefix)
                            .stream(new ByteArrayInputStream(new byte[]{}), 0, -1)
                            .contentType("application/x-directory")
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create root folder prefix for trash: " + prefix, e);
        }

        return saved;
    }

    private String rootPrefix(User user) {
        return "users/" + user.getId() + "/";
    }
}