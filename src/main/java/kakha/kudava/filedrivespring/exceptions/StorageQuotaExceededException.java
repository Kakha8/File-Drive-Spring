package kakha.kudava.filedrivespring.exceptions;

public class StorageQuotaExceededException extends RuntimeException {
    public StorageQuotaExceededException(long limitBytes) {
        super("The application storage limit of " + limitBytes + " bytes has been reached.");
    }
}
