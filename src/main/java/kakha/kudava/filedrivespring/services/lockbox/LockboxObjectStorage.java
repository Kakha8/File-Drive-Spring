package kakha.kudava.filedrivespring.services.lockbox;

import java.io.InputStream;
import java.nio.file.Path;

public interface LockboxObjectStorage {

    void upload(
            String objectKey,
            Path source
    ) throws Exception;

    InputStream download(String objectKey) throws Exception;

    void delete(String objectKey) throws Exception;
}
