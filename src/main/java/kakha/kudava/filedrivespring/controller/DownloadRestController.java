package kakha.kudava.filedrivespring.controller;

import jakarta.servlet.http.HttpServletResponse;
import kakha.kudava.filedrivespring.dto.DownloadZipRequest;
import kakha.kudava.filedrivespring.records.ZipDownloadResult;
import kakha.kudava.filedrivespring.services.DownloadService;
import kakha.kudava.filedrivespring.services.DownloadTicketService;
import kakha.kudava.filedrivespring.services.ResourceAccessService;
import kakha.kudava.filedrivespring.services.objects.FolderService;
import kakha.kudava.filedrivespring.services.objects.ObjectStorageService;
import kakha.kudava.filedrivespring.model.FileMetaData;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/download")
public class DownloadRestController {

    private final DownloadService downloadService;
    private final DownloadTicketService downloadTickets;
    private final ResourceAccessService access;
    private final ObjectStorageService storage;
    private final FolderService folderService;

    public DownloadRestController(DownloadService downloadService, DownloadTicketService downloadTickets,
                                  ResourceAccessService access, ObjectStorageService storage, FolderService folderService) {
        this.downloadService = downloadService;
        this.downloadTickets = downloadTickets;
        this.access = access;
        this.storage = storage;
        this.folderService = folderService;
    }

    @PostMapping("/ticket")
    public ResponseEntity<Map<String, String>> createTicket(@RequestBody DownloadTicketRequest request,
                                                             Authentication authentication) {
        List<Long> fileIds = request.fileIds() == null ? List.of() : request.fileIds();
        List<Long> folderIds = request.folderIds() == null ? List.of() : request.folderIds();
        DownloadTicketService.Kind kind = DownloadTicketService.Kind.valueOf(request.kind().toUpperCase());

        if (kind == DownloadTicketService.Kind.FILE && (fileIds.size() != 1 || !folderIds.isEmpty())) {
            throw new IllegalArgumentException("A file download requires exactly one file");
        }
        if (kind == DownloadTicketService.Kind.FOLDER && (folderIds.size() != 1 || !fileIds.isEmpty())) {
            throw new IllegalArgumentException("A folder download requires exactly one folder");
        }
        if (kind == DownloadTicketService.Kind.ZIP && fileIds.isEmpty() && folderIds.isEmpty()) {
            throw new IllegalArgumentException("No items selected");
        }

        fileIds.forEach(access::requireFileView);
        folderIds.forEach(access::requireFolderView);
        return ResponseEntity.ok(Map.of("ticket", downloadTickets.issue(authentication.getName(), kind, fileIds, folderIds)));
    }

    @GetMapping("/browser")
    public void browserDownload(@RequestParam String ticket, Authentication authentication,
                                HttpServletResponse response) throws Exception {
        DownloadTicketService.Ticket issued = downloadTickets.consume(ticket);
        if (issued == null || authentication == null || !issued.username().equals(authentication.getName())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Download ticket is invalid or expired");
            return;
        }

        switch (issued.kind()) {
            case FILE -> streamFile(issued.fileIds().getFirst(), response);
            case FOLDER -> {
                var result = folderService.downloadFolderAsZip(issued.folderIds().getFirst());
                prepareAttachment(response, result.fileName(), MediaType.APPLICATION_OCTET_STREAM_VALUE, null);
                result.responseBody().writeTo(response.getOutputStream());
            }
            case ZIP -> {
                DownloadZipRequest request = new DownloadZipRequest();
                request.setFileIds(issued.fileIds());
                request.setFolderIds(issued.folderIds());
                var result = downloadService.downloadAsZip(request);
                prepareAttachment(response, result.fileName(), MediaType.APPLICATION_OCTET_STREAM_VALUE, null);
                result.responseBody().writeTo(response.getOutputStream());
            }
        }
    }

    private void streamFile(Long id, HttpServletResponse response) throws Exception {
        FileMetaData meta = storage.getMeta(id);
        String contentType = meta.getObjectType();
        if (contentType == null || contentType.isBlank()) contentType = URLConnection.guessContentTypeFromName(meta.getFileName());
        if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        prepareAttachment(response, meta.getFileName(), contentType, meta.getSize());
        try (InputStream input = storage.download(id)) {
            input.transferTo(response.getOutputStream());
        }
    }

    private void prepareAttachment(HttpServletResponse response, String fileName, String contentType, Long length) {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName.replace("\"", "") + "\"");
        response.setContentType(contentType);
        if (length != null && length >= 0) response.setContentLengthLong(length);
    }

    public record DownloadTicketRequest(String kind, List<Long> fileIds, List<Long> folderIds) {}

    @PostMapping("/zip")
    public void downloadZip(
            @RequestBody DownloadZipRequest request,
            HttpServletResponse response
    ) throws Exception {
        ZipDownloadResult result = downloadService.downloadAsZip(request);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + result.fileName() + "\""
        );
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        result.responseBody().writeTo(response.getOutputStream());
    }
}
