package kakha.kudava.filedrivespring.records;
import java.io.InputStream;
public record LockboxDownloadResult(String fileName,long size,String contentType,InputStream inputStream) {}
