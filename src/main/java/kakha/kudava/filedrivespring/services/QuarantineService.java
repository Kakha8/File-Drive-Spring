package kakha.kudava.filedrivespring.services;

import io.minio.MinioClient;
import kakha.kudava.filedrivespring.dto.ViewQuarantinedFilesDTO;
import kakha.kudava.filedrivespring.model.QuarantinedFiles;
import kakha.kudava.filedrivespring.repository.QuarantinedFilesRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import io.minio.GetObjectArgs;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
public class QuarantineService {

    private final QuarantinedFilesRepository quarantinedFilesRepository;
    private final MinioClient minioClient;
    private final String quarantineBucket;

    public QuarantineService(QuarantinedFilesRepository quarantinedFilesRepository, MinioClient minioClient,
                             @Value("${s3.quarantine-bucket}") String quarantineBucket) {
        this.quarantinedFilesRepository = quarantinedFilesRepository;
        this.minioClient = minioClient;
        this.quarantineBucket = quarantineBucket;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public QuarantinedFiles save(QuarantinedFiles quarantinedFile) {
        return quarantinedFilesRepository.saveAndFlush(quarantinedFile);
    }

    @Transactional(readOnly = true)
    public List<ViewQuarantinedFilesDTO> viewQuarantine() {
        return quarantinedFilesRepository.findAll()
                .stream()
                .map(q -> new ViewQuarantinedFilesDTO(
                        q.getId(),
                        q.getOriginalFilename(),
                        q.getObjectKey(),
                        q.getContentType(),
                        q.getSize(),
                        q.getChecksum(),
                        q.getClamAvResponse(),
                        q.getParentFolderId(),
                        q.getUser().getId(),
                        q.getUser().getUsername(),
                        q.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ViewQuarantinedFilesDTO findQuarantinedFileById(Long id) {
        QuarantinedFiles q = quarantinedFilesRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Quarantined file not found: " + id));

        return new ViewQuarantinedFilesDTO(
                q.getId(),
                q.getOriginalFilename(),
                q.getObjectKey(),
                q.getContentType(),
                q.getSize(),
                q.getChecksum(),
                q.getClamAvResponse(),
                q.getParentFolderId(),
                q.getUser().getId(),
                q.getUser().getUsername(),
                q.getCreatedAt()
        );
    }

    public Path createPasswordProtectedZip(Long quarantineId) throws Exception {
        QuarantinedFiles q = quarantinedFilesRepository.findById(quarantineId)
                .orElseThrow(() -> new RuntimeException("Quarantined file not found: " + quarantineId));

        Path tempDir = Files.createTempDirectory("quarantine-download-");
        Path infectedFile = tempDir.resolve(q.getOriginalFilename());
        Path zipFile = tempDir.resolve("quarantine-" + q.getId() + ".zip");

        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(quarantineBucket)
                        .object(q.getObjectKey())
                        .build()
        )) {
            Files.copy(in, infectedFile, StandardCopyOption.REPLACE_EXISTING);
        }

        ZipParameters zipParameters = new ZipParameters();
        zipParameters.setEncryptFiles(true);
        zipParameters.setEncryptionMethod(EncryptionMethod.AES);
        zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
        zipParameters.setFileNameInZip(q.getOriginalFilename());

        ZipFile zip = new ZipFile(zipFile.toFile(), "infected".toCharArray());
        zip.addFile(infectedFile.toFile(), zipParameters);

        return zipFile;
    }
}
