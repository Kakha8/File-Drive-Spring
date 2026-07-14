package kakha.kudava.filedrivespring.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import kakha.kudava.filedrivespring.dto.TextFileContentDTO;
import kakha.kudava.filedrivespring.dto.UpdateTextFileRequest;
import kakha.kudava.filedrivespring.enums.EntityType;
import kakha.kudava.filedrivespring.exceptions.MalwareDetectedException;
import kakha.kudava.filedrivespring.model.FileMetaData;
import kakha.kudava.filedrivespring.repository.FileMetaDataRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class TextFileService {

    private static final String TEXT_CONTENT_TYPE =
            "text/plain; charset=UTF-8";

    private final MinioClient minioClient;
    private final String bucket;
    private final long maxBytes;
    private final FileMetaDataRepository fileMetaDataRepository;
    private final ResourceAccessService resourceAccessService;
    private final ClamAvScannerService clamAvScannerService;
    private final LogsService logsService;
    private final ObjectMapper objectMapper;

    public TextFileService(
            MinioClient minioClient,
            @Value("${s3.bucket}") String bucket,
            @Value("${app.text-editor.max-bytes:1048576}") long maxBytes,
            FileMetaDataRepository fileMetaDataRepository,
            ResourceAccessService resourceAccessService,
            ClamAvScannerService clamAvScannerService,
            LogsService logsService,
            ObjectMapper objectMapper
    ) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.maxBytes = maxBytes;
        this.fileMetaDataRepository = fileMetaDataRepository;
        this.resourceAccessService = resourceAccessService;
        this.clamAvScannerService = clamAvScannerService;
        this.logsService = logsService;
        this.objectMapper = objectMapper;
    }

    public TextFileContentDTO getContent(Long fileId) throws Exception {
        // Checks that the file exists and the current user may view it.
        FileMetaData file =
                resourceAccessService.requireFileView(fileId);

        requireTxtFile(file);

        byte[] bytes;

        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(file.getObjectKey())
                        .build()
        )) {
            bytes = readWithLimit(inputStream);
        }

        String content = decodeUtf8(bytes);

        return toDto(file, content);
    }

    public TextFileContentDTO updateContent(
            Long fileId,
            UpdateTextFileRequest request
    ) throws Exception {

        // Checks that the current user has edit permission.
        FileMetaData file =
                resourceAccessService.requireFileEdit(fileId);

        requireTxtFile(file);

        if (request == null || request.getContent() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "content is required"
            );
        }

        checkExpectedChecksum(file, request.getExpectedChecksum());

        byte[] newBytes =
                request.getContent().getBytes(StandardCharsets.UTF_8);

        requireWithinLimit(newBytes.length);

        String newChecksum = sha256(newBytes);

        // Content has not changed, so there is no need to rewrite MinIO.
        if (newChecksum.equalsIgnoreCase(file.getChecksum())) {
            return toDto(file, request.getContent());
        }

        scanContent(newBytes);

        String oldChecksum = file.getChecksum();
        Long oldSize = file.getSize();

        replaceMinioObject(file, newBytes);

        file.setChecksum(newChecksum);
        file.setSize((long) newBytes.length);
        file.setObjectType(TEXT_CONTENT_TYPE);

        FileMetaData savedFile =
                fileMetaDataRepository.save(file);

        createUpdateLog(
                savedFile,
                oldChecksum,
                oldSize,
                newChecksum,
                newBytes.length
        );

        return toDto(savedFile, request.getContent());
    }

    private void replaceMinioObject(
            FileMetaData file,
            byte[] content
    ) throws Exception {

        try (InputStream inputStream =
                     new ByteArrayInputStream(content)) {

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(file.getObjectKey())
                            .stream(
                                    inputStream,
                                    content.length,
                                    -1
                            )
                            .contentType(TEXT_CONTENT_TYPE)
                            .build()
            );
        }
    }

    private void scanContent(byte[] content) throws Exception {
        Path temporaryFile =
                Files.createTempFile("text-edit-", ".txt");

        try {
            Files.write(temporaryFile, content);

            ClamAvScannerService.ScanResult scanResult =
                    clamAvScannerService.scan(temporaryFile);

            if (!scanResult.clean()) {
                throw new MalwareDetectedException(
                        "Edit rejected: malware detected"
                );
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private void checkExpectedChecksum(
            FileMetaData file,
            String expectedChecksum
    ) {
        if (expectedChecksum == null ||
                expectedChecksum.isBlank()) {
            return;
        }

        if (!expectedChecksum.equalsIgnoreCase(file.getChecksum())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The file changed after you loaded it. " +
                            "Reload the content and try again."
            );
        }
    }

    private TextFileContentDTO toDto(
            FileMetaData file,
            String content
    ) {
        return new TextFileContentDTO(
                file.getId(),
                file.getFileName(),
                file.getObjectType(),
                StandardCharsets.UTF_8.name(),
                file.getSize(),
                file.getChecksum(),
                content
        );
    }

    private void requireTxtFile(FileMetaData file) {
        String fileName = file.getFileName();

        if (fileName == null ||
                !fileName.toLowerCase(Locale.ROOT)
                        .endsWith(".txt")) {

            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only .txt files can be edited"
            );
        }
    }

    private byte[] readWithLimit(
            InputStream inputStream
    ) throws Exception {

        try (ByteArrayOutputStream outputStream =
                     new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            long totalBytes = 0;
            int numberOfBytesRead;

            while ((numberOfBytesRead =
                    inputStream.read(buffer)) != -1) {

                totalBytes += numberOfBytesRead;
                requireWithinLimit(totalBytes);

                outputStream.write(
                        buffer,
                        0,
                        numberOfBytesRead
                );
            }

            return outputStream.toByteArray();
        }
    }

    private void requireWithinLimit(long byteCount) {
        if (byteCount > maxBytes) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Text file exceeds the editor limit of " +
                            maxBytes + " bytes"
            );
        }
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(
                            CodingErrorAction.REPORT
                    )
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();

        } catch (CharacterCodingException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "The .txt file is not valid UTF-8 text"
            );
        }
    }

    private String sha256(byte[] bytes) throws Exception {
        MessageDigest messageDigest =
                MessageDigest.getInstance("SHA-256");

        byte[] digest = messageDigest.digest(bytes);

        StringBuilder result =
                new StringBuilder(digest.length * 2);

        for (byte value : digest) {
            result.append(String.format("%02x", value));
        }

        return result.toString();
    }

    private void createUpdateLog(
            FileMetaData file,
            String oldChecksum,
            Long oldSize,
            String newChecksum,
            long newSize
    ) throws Exception {

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("fileName", file.getFileName());
        details.put("oldChecksum", oldChecksum);
        details.put("newChecksum", newChecksum);
        details.put("oldSize", oldSize);
        details.put("newSize", newSize);

        logsService.updateLog(
                file.getFileName(),
                file.getId(),
                EntityType.FILE.name(),
                objectMapper.writeValueAsString(details)
        );
    }
}