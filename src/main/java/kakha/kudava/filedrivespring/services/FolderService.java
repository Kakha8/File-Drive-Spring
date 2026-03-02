package kakha.kudava.filedrivespring.services;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import kakha.kudava.filedrivespring.dto.FolderDTO;
import kakha.kudava.filedrivespring.model.Folders;
import kakha.kudava.filedrivespring.repository.FolderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Service
public class FolderService {

    private final FolderRepository folderRepository;

    @Value("${s3.bucket}")
    private String bucket;
    private final MinioClient minioClient;
    public FolderService(FolderRepository folderRepository, MinioClient minioClient) {
        this.folderRepository = folderRepository;
        this.minioClient = minioClient;
    }

    public FolderDTO create(FolderDTO folderDTO)
            throws ServerException,
            InsufficientDataException,
            ErrorResponseException,
            IOException,
            NoSuchAlgorithmException,
            InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {

        String name = folderDTO.getName();
        String prefix = folderDTO.getPrefix();

        String normalizedName = name.replaceAll("^/|/$", "");
        String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        String folderKey = normalizedPrefix + normalizedName + "/";

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(folderKey)
                        .stream(
                                new ByteArrayInputStream(new byte[]{}),
                                0,
                                -1
                        )
                        .build()
        );
        Folders folder = new Folders();
        folder.setName(name);
        folder.setPrefix(prefix);
        folderRepository.save(folder);

        return folderDTO;
    }

}
