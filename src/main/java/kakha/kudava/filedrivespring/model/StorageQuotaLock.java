package kakha.kudava.filedrivespring.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "storage_quota_lock")
public class StorageQuotaLock {
    @Id
    private Long id;

    protected StorageQuotaLock() {
    }

    public StorageQuotaLock(Long id) {
        this.id = id;
    }
}
