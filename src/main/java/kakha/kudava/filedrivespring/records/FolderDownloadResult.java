package kakha.kudava.filedrivespring.records;

import java.io.InputStream;

public record FolderDownloadResult(String fileName, InputStream inputStream) {
}