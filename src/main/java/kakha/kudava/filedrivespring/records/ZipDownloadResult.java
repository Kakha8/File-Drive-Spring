package kakha.kudava.filedrivespring.records;

import java.io.InputStream;

public record ZipDownloadResult(String fileName,
                                InputStream inputStream
) {
}
