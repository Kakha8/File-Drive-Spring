package kakha.kudava.filedrivespring.services;

import kakha.kudava.filedrivespring.dto.DownloadZipRequest;

import kakha.kudava.filedrivespring.model.FileMetaData;
import kakha.kudava.filedrivespring.model.Folders;
import kakha.kudava.filedrivespring.records.ZipDownloadResult;
import kakha.kudava.filedrivespring.repository.FileMetaDataRepository;
import kakha.kudava.filedrivespring.repository.FolderRepository;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DownloadService {

    private final FileMetaDataRepository fileMetaDataRepository;
    private final FolderRepository folderRepository;
    private final ObjectStorageService objectStorageService;

    public DownloadService(
            FileMetaDataRepository fileMetaDataRepository,
            FolderRepository folderRepository,
            ObjectStorageService objectStorageService
    ) {
        this.fileMetaDataRepository = fileMetaDataRepository;
        this.folderRepository = folderRepository;
        this.objectStorageService = objectStorageService;
    }

    public ZipDownloadResult downloadAsZip(DownloadZipRequest request) throws Exception {
        List<Long> fileIds = request.getFileIds() == null ? List.of() : request.getFileIds();
        List<Long> folderIds = request.getFolderIds() == null ? List.of() : request.getFolderIds();

        if (fileIds.isEmpty() && folderIds.isEmpty()) {
            throw new RuntimeException("No items selected");
        }

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Set<String> usedZipNames = new HashSet<>();

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            for (Long fileId : fileIds) {
                FileMetaData file = fileMetaDataRepository.findById(fileId)
                        .orElseThrow(() -> new RuntimeException("File not found: " + fileId));

                addFileToZip(file, "", zipOutputStream, usedZipNames);
            }

            for (Long folderId : folderIds) {
                Folders folder = folderRepository.findById(folderId)
                        .orElseThrow(() -> new RuntimeException("Folder not found: " + folderId));

                String folderPath = uniqueZipName(
                        sanitizeZipName(folder.getName()) + "/",
                        usedZipNames
                );

                addFolderToZip(folder, folderPath, zipOutputStream, usedZipNames);
            }
        }

        return new ZipDownloadResult(
                "download.zip",
                new ByteArrayInputStream(byteArrayOutputStream.toByteArray())
        );
    }

    private void addFolderToZip(
            Folders folder,
            String currentPath,
            ZipOutputStream zipOutputStream,
            Set<String> usedZipNames
    ) throws Exception {
        addDirectoryEntry(currentPath, zipOutputStream, usedZipNames);

        List<FileMetaData> files = fileMetaDataRepository.findByParentId(folder.getId());

        for (FileMetaData file : files) {
            addFileToZip(file, currentPath, zipOutputStream, usedZipNames);
        }

        List<Folders> childFolders = folderRepository.findByParentId(folder.getId());

        for (Folders childFolder : childFolders) {
            String childPath = uniqueZipName(
                    currentPath + sanitizeZipName(childFolder.getName()) + "/",
                    usedZipNames
            );

            addFolderToZip(childFolder, childPath, zipOutputStream, usedZipNames);
        }
    }

    private void addFileToZip(
            FileMetaData file,
            String folderPath,
            ZipOutputStream zipOutputStream,
            Set<String> usedZipNames
    ) throws Exception {
        String safeFileName = sanitizeZipName(file.getFileName());
        String zipPath = uniqueZipName(folderPath + safeFileName, usedZipNames);

        try (InputStream fileInputStream = objectStorageService.download(file.getId())) {
            ZipEntry fileEntry = new ZipEntry(zipPath);
            zipOutputStream.putNextEntry(fileEntry);

            fileInputStream.transferTo(zipOutputStream);

            zipOutputStream.closeEntry();
        }
    }

    private void addDirectoryEntry(
            String folderPath,
            ZipOutputStream zipOutputStream,
            Set<String> usedZipNames
    ) throws Exception {
        String path = folderPath.endsWith("/") ? folderPath : folderPath + "/";

        if (usedZipNames.contains(path)) {
            return;
        }

        usedZipNames.add(path);

        ZipEntry folderEntry = new ZipEntry(path);
        zipOutputStream.putNextEntry(folderEntry);
        zipOutputStream.closeEntry();
    }

    private String sanitizeZipName(String name) {
        if (name == null || name.isBlank()) {
            return "untitled";
        }

        return name
                .replace("\\", "_")
                .replace("/", "_")
                .replace(":", "_")
                .replace("*", "_")
                .replace("?", "_")
                .replace("\"", "_")
                .replace("<", "_")
                .replace(">", "_")
                .replace("|", "_")
                .trim();
    }

    private String uniqueZipName(String desiredName, Set<String> usedZipNames) {
        if (!usedZipNames.contains(desiredName)) {
            usedZipNames.add(desiredName);
            return desiredName;
        }

        boolean isDirectory = desiredName.endsWith("/");
        String cleanName = isDirectory
                ? desiredName.substring(0, desiredName.length() - 1)
                : desiredName;

        String parentPath = "";
        String baseName = cleanName;
        String extension = "";

        int slashIndex = cleanName.lastIndexOf("/");
        if (slashIndex >= 0) {
            parentPath = cleanName.substring(0, slashIndex + 1);
            baseName = cleanName.substring(slashIndex + 1);
        }

        if (!isDirectory) {
            int dotIndex = baseName.lastIndexOf(".");
            if (dotIndex > 0) {
                extension = baseName.substring(dotIndex);
                baseName = baseName.substring(0, dotIndex);
            }
        }

        int counter = 1;

        while (true) {
            String candidate = parentPath + baseName + " (" + counter + ")" + extension;

            if (isDirectory) {
                candidate += "/";
            }

            if (!usedZipNames.contains(candidate)) {
                usedZipNames.add(candidate);
                return candidate;
            }

            counter++;
        }
    }
}