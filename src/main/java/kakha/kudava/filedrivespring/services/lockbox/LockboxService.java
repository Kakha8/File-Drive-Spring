package kakha.kudava.filedrivespring.services.lockbox;

import kakha.kudava.filedrivespring.dto.LockboxFileItemResponse;
import kakha.kudava.filedrivespring.dto.LockboxFolderItemResponse;
import kakha.kudava.filedrivespring.dto.LockboxFolderViewResponse;
import kakha.kudava.filedrivespring.dto.LockboxUploadResponse;
import kakha.kudava.filedrivespring.enums.DriveSpace;
import kakha.kudava.filedrivespring.model.FileMetaData;
import kakha.kudava.filedrivespring.model.Folders;
import kakha.kudava.filedrivespring.model.LockboxFile;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.records.LockboxContainerInfo;
import kakha.kudava.filedrivespring.records.LockboxDownloadResult;
import kakha.kudava.filedrivespring.repository.FileMetaDataRepository;
import kakha.kudava.filedrivespring.repository.FolderRepository;
import kakha.kudava.filedrivespring.repository.LockboxFileRepository;
import kakha.kudava.filedrivespring.services.ResourceAccessService;
import kakha.kudava.filedrivespring.services.objects.RootFolderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class LockboxService {

    private static final String LOCKBOX_OBJECT_TYPE =
            "application/x-filedrive-lockbox";

    private static final String DEFAULT_FILE_NAME =
            "encrypted-file.fdcse";

    private final LockboxContainerValidator containerValidator;
    private final LockboxObjectStorage objectStorage;
    private final FileMetaDataRepository fileMetaDataRepository;
    private final FolderRepository folderRepository;
    private final LockboxFileRepository lockboxFileRepository;
    private final RootFolderService rootFolderService;
    private final ResourceAccessService access;

    public LockboxService(
            LockboxContainerValidator containerValidator,
            LockboxObjectStorage objectStorage,
            FileMetaDataRepository fileMetaDataRepository,
            FolderRepository folderRepository,
            LockboxFileRepository lockboxFileRepository,
            RootFolderService rootFolderService,
            ResourceAccessService access
    ) {
        this.containerValidator = containerValidator;
        this.objectStorage = objectStorage;
        this.fileMetaDataRepository = fileMetaDataRepository;
        this.folderRepository = folderRepository;
        this.lockboxFileRepository = lockboxFileRepository;
        this.rootFolderService = rootFolderService;
        this.access = access;
    }

    /**
     * Uploads one already client-encrypted Lockbox container.
     */
    @Transactional(rollbackFor = Exception.class)
    public LockboxUploadResponse upload(
            MultipartFile encryptedFile,
            Long parentFolderId
    ) throws Exception {
        validateMultipart(encryptedFile);

        User user = access.currentUser();
        Folders parent = resolveUploadParent(
                parentFolderId,
                user
        );

        Path stagingFile = Files.createTempFile(
                "lockbox-upload-",
                ".fdcse"
        );

        String objectKey = null;
        boolean objectUploaded = false;

        try {
            copyToStagingFile(
                    encryptedFile,
                    stagingFile
            );

            LockboxContainerInfo containerInfo =
                    containerValidator.validate(stagingFile);

            long ciphertextSize = Files.size(stagingFile);
            String ciphertextChecksum =
                    calculateSha256(stagingFile);

            objectKey = generateObjectKey(user);
            objectStorage.upload(objectKey, stagingFile);
            objectUploaded = true;

            FileMetaData file = createFileMetadata(
                    encryptedFile,
                    user,
                    parent,
                    objectKey,
                    ciphertextSize,
                    ciphertextChecksum
            );

            FileMetaData savedFile =
                    fileMetaDataRepository.saveAndFlush(file);

            LockboxFile lockboxFile = new LockboxFile(
                    savedFile,
                    containerInfo.formatVersion(),
                    containerInfo.algorithmSuite(),
                    containerInfo.chunkSize(),
                    null,
                    null
            );

            LockboxFile savedLockboxFile =
                    lockboxFileRepository.saveAndFlush(
                            lockboxFile
                    );

            return new LockboxUploadResponse(
                    savedFile.getId(),
                    savedFile.getFileName(),
                    parent.getId(),
                    savedFile.getSize(),
                    savedFile.getChecksum(),
                    savedLockboxFile.getFormatVersion(),
                    savedLockboxFile.getAlgorithmSuite(),
                    savedLockboxFile.getChunkSize(),
                    savedLockboxFile.getCreatedAt()
            );
        } catch (Exception originalFailure) {
            if (objectUploaded && objectKey != null) {
                try {
                    objectStorage.delete(objectKey);
                } catch (Exception cleanupFailure) {
                    originalFailure.addSuppressed(
                            cleanupFailure
                    );
                }
            }

            throw originalFailure;
        } finally {
            try {
                Files.deleteIfExists(stagingFile);
            } catch (IOException ignored) {
                // Do not hide the original upload result.
            }
        }
    }

    /**
     * Returns the Lockbox root and its direct children.
     */
    @Transactional(readOnly = true)
    public LockboxFolderViewResponse viewRoot() {
        User user = access.currentUser();

        Folders root =
                rootFolderService.ensureLockboxRootFolder(user);

        return buildFolderView(root, user);
    }

    /**
     * Returns one owned Lockbox folder and its direct children.
     */
    @Transactional(readOnly = true)
    public LockboxFolderViewResponse viewFolder(Long folderId) {
        if (folderId == null) {
            throw new IllegalArgumentException(
                    "Folder ID is required."
            );
        }

        User user = access.currentUser();
        Folders folder = requireOwnedLockboxFolder(
                folderId,
                user
        );

        return buildFolderView(folder, user);
    }

    /**
     * Opens the encrypted MinIO object for streaming to the client.
     */
    @Transactional(readOnly = true)
    public LockboxDownloadResult openDownload(
            Long fileId
    ) throws Exception {
        if (fileId == null) {
            throw new IllegalArgumentException(
                    "File ID is required."
            );
        }

        User user = access.currentUser();

        FileMetaData file = fileMetaDataRepository
                .findById(fileId)
                .orElseThrow(() -> new RuntimeException(
                        "Lockbox file not found."
                ));

        requireOwnedLockboxFile(file, user);

        if (!lockboxFileRepository.existsById(fileId)) {
            throw new IllegalStateException(
                    "Lockbox metadata is missing for file "
                            + fileId
                            + "."
            );
        }

        InputStream input =
                objectStorage.download(file.getObjectKey());

        return new LockboxDownloadResult(
                file.getFileName(),
                file.getSize(),
                input
        );
    }

    private LockboxFolderViewResponse buildFolderView(
            Folders parent,
            User user
    ) {
        List<Folders> childFolders =
                folderRepository
                        .findFoldersByParent_Id(parent.getId())
                        .stream()
                        .filter(folder ->
                                folder.getDriveSpace()
                                        == DriveSpace.LOCKBOX
                        )
                        .filter(folder ->
                                !folder.isDeleted()
                        )
                        .filter(folder ->
                                !folder.isPermanentlyDeleted()
                        )
                        .filter(folder ->
                                isOwnedBy(folder, user)
                        )
                        .sorted(
                                Comparator.comparing(
                                        Folders::getName,
                                        String.CASE_INSENSITIVE_ORDER
                                )
                        )
                        .toList();

        List<FileMetaData> files =
                fileMetaDataRepository
                        .findByParent_IdAndDeletedFalse(
                                parent.getId()
                        )
                        .stream()
                        .filter(file ->
                                file.getDriveSpace()
                                        == DriveSpace.LOCKBOX
                        )
                        .filter(file ->
                                !file.isPermanentlyDeleted()
                        )
                        .filter(file ->
                                isOwnedBy(file, user)
                        )
                        .sorted(
                                Comparator.comparing(
                                        FileMetaData::getFileName,
                                        String.CASE_INSENSITIVE_ORDER
                                )
                        )
                        .toList();

        List<Long> fileIds = files.stream()
                .map(FileMetaData::getId)
                .toList();

        Map<Long, LockboxFile> lockboxByFileId =
                new HashMap<>();

        lockboxFileRepository
                .findAllById(fileIds)
                .forEach(lockbox ->
                        lockboxByFileId.put(
                                lockbox.getId(),
                                lockbox
                        )
                );

        List<LockboxFolderItemResponse> folderResponses =
                childFolders.stream()
                        .map(folder ->
                                new LockboxFolderItemResponse(
                                        folder.getId(),
                                        folder.getName(),
                                        parent.getId()
                                )
                        )
                        .toList();

        List<LockboxFileItemResponse> fileResponses =
                new ArrayList<>();

        for (FileMetaData file : files) {
            LockboxFile lockbox =
                    lockboxByFileId.get(file.getId());

            if (lockbox == null) {
                throw new IllegalStateException(
                        "Lockbox metadata is missing for file "
                                + file.getId()
                                + "."
                );
            }

            fileResponses.add(
                    new LockboxFileItemResponse(
                            file.getId(),
                            file.getFileName(),
                            file.getSize(),
                            file.getChecksum(),
                            file.getCreationDate(),
                            lockbox.getFormatVersion(),
                            lockbox.getAlgorithmSuite(),
                            lockbox.getChunkSize()
                    )
            );
        }

        return new LockboxFolderViewResponse(
                parent.getId(),
                parent.getName(),
                parent.getParent() == null
                        ? null
                        : parent.getParent().getId(),
                folderResponses,
                fileResponses
        );
    }

    private Folders resolveUploadParent(
            Long parentFolderId,
            User user
    ) {
        if (parentFolderId == null) {
            return rootFolderService
                    .ensureLockboxRootFolder(user);
        }

        return requireOwnedLockboxFolder(
                parentFolderId,
                user
        );
    }

    private Folders requireOwnedLockboxFolder(
            Long folderId,
            User user
    ) {
        Folders folder =
                access.requireFolderOwner(folderId);

        if (folder.isDeleted()
                || folder.isPermanentlyDeleted()) {
            throw new IllegalArgumentException(
                    "The Lockbox folder is deleted."
            );
        }

        if (folder.getDriveSpace()
                != DriveSpace.LOCKBOX) {
            throw new IllegalArgumentException(
                    "Destination is not a Lockbox folder."
            );
        }

        if (!isOwnedBy(folder, user)) {
            throw new IllegalArgumentException(
                    "Lockbox folder access denied."
            );
        }

        return folder;
    }

    private void requireOwnedLockboxFile(
            FileMetaData file,
            User user
    ) {
        if (file.isDeleted()
                || file.isPermanentlyDeleted()) {
            throw new RuntimeException(
                    "Lockbox file not found."
            );
        }

        if (file.getDriveSpace()
                != DriveSpace.LOCKBOX) {
            throw new IllegalArgumentException(
                    "The requested file is not a Lockbox file."
            );
        }

        if (!isOwnedBy(file, user)) {
            throw new RuntimeException(
                    "Lockbox file not found."
            );
        }

        if (file.getObjectKey() == null
                || file.getObjectKey().isBlank()) {
            throw new IllegalStateException(
                    "Lockbox object key is missing."
            );
        }
    }

    private boolean isOwnedBy(
            Folders folder,
            User user
    ) {
        return folder.getOwner() != null
                && Objects.equals(
                        folder.getOwner().getId(),
                        user.getId()
                );
    }

    private boolean isOwnedBy(
            FileMetaData file,
            User user
    ) {
        return file.getOwner() != null
                && Objects.equals(
                        file.getOwner().getId(),
                        user.getId()
                );
    }

    private void validateMultipart(MultipartFile encryptedFile) {
        if (encryptedFile == null) {
            throw new IllegalArgumentException(
                    "Encrypted Lockbox file is required."
            );
        }

        if (encryptedFile.isEmpty()) {
            throw new IllegalArgumentException(
                    "Encrypted Lockbox file cannot be empty."
            );
        }
    }

    private void copyToStagingFile(
            MultipartFile encryptedFile,
            Path stagingFile
    ) throws IOException {
        try (InputStream input =
                     encryptedFile.getInputStream()) {
            Files.copy(
                    input,
                    stagingFile,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }

        if (Files.size(stagingFile) == 0) {
            throw new IllegalArgumentException(
                    "Encrypted Lockbox file cannot be empty."
            );
        }
    }

    private FileMetaData createFileMetadata(
            MultipartFile encryptedFile,
            User user,
            Folders parent,
            String objectKey,
            long ciphertextSize,
            String ciphertextChecksum
    ) {
        FileMetaData file = new FileMetaData();

        file.setObjectKey(objectKey);
        file.setFileName(
                sanitizeDisplayName(
                        encryptedFile.getOriginalFilename()
                )
        );
        file.setObjectType(LOCKBOX_OBJECT_TYPE);
        file.setChecksum(ciphertextChecksum);
        file.setCreationDate(Instant.now());
        file.setSize(ciphertextSize);
        file.setDeleted(false);
        file.setDeletedAt(null);
        file.setPermanentlyDeleted(false);
        file.setPermanentlyDeletedAt(null);
        file.setOriginalObjectKey(null);
        file.setOwner(user);
        file.setParent(parent);
        file.setDriveSpace(DriveSpace.LOCKBOX);

        return file;
    }

    private String generateObjectKey(User user) {
        return "users/"
                + user.getId()
                + "/objects/"
                + UUID.randomUUID()
                + ".fdcse";
    }

    private String calculateSha256(Path path)
            throws IOException {
        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance(
                    "SHA-256"
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception
            );
        }

        try (
                InputStream input = Files.newInputStream(path);
                DigestInputStream digestInput =
                        new DigestInputStream(
                                input,
                                digest
                        )
        ) {
            byte[] buffer = new byte[64 * 1024];

            while (digestInput.read(buffer) != -1) {
                // DigestInputStream updates the digest.
            }
        }

        return HexFormat.of().formatHex(
                digest.digest()
        );
    }

    private String sanitizeDisplayName(
            String originalFilename
    ) {
        if (originalFilename == null
                || originalFilename.isBlank()) {
            return DEFAULT_FILE_NAME;
        }

        String normalized = originalFilename
                .trim()
                .replace('\\', '/');

        int lastSlash = normalized.lastIndexOf('/');

        if (lastSlash >= 0) {
            normalized = normalized.substring(
                    lastSlash + 1
            );
        }

        normalized = normalized
                .replaceAll("[\\p{Cntrl}]", "_")
                .trim();

        if (normalized.isBlank()) {
            return DEFAULT_FILE_NAME;
        }

        if (normalized.length() > 255) {
            normalized = normalized.substring(0, 255);
        }

        return normalized;
    }
}
