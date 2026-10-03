package kakha.kudava.filedrivespring.services;

import kakha.kudava.filedrivespring.exceptions.StorageQuotaExceededException;
import kakha.kudava.filedrivespring.model.StorageQuotaLock;
import kakha.kudava.filedrivespring.model.StorageReservation;
import kakha.kudava.filedrivespring.repository.FileMetaDataRepository;
import kakha.kudava.filedrivespring.repository.LockboxFileRevisionRepository;
import kakha.kudava.filedrivespring.repository.QuarantinedFilesRepository;
import kakha.kudava.filedrivespring.repository.StorageQuotaLockRepository;
import kakha.kudava.filedrivespring.repository.StorageReservationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class StorageQuotaService {
    public static final long DEFAULT_LIMIT_BYTES = 2_199_023_255_552L;
    private static final long LOCK_ID = 1L;

    private final long limitBytes;
    private final Duration reservationTtl;
    private final FileMetaDataRepository files;
    private final QuarantinedFilesRepository quarantine;
    private final LockboxFileRevisionRepository lockboxRevisions;
    private final StorageQuotaLockRepository quotaLocks;
    private final StorageReservationRepository reservations;
    private final TransactionTemplate transaction;

    public StorageQuotaService(
            @Value("${storage.quota.limit-bytes:2199023255552}") long limitBytes,
            @Value("${storage.quota.reservation-ttl-hours:24}") long reservationTtlHours,
            FileMetaDataRepository files,
            QuarantinedFilesRepository quarantine,
            LockboxFileRevisionRepository lockboxRevisions,
            StorageQuotaLockRepository quotaLocks,
            StorageReservationRepository reservations,
            PlatformTransactionManager transactionManager
    ) {
        if (limitBytes < 1 || reservationTtlHours < 1) {
            throw new IllegalArgumentException("Storage quota settings must be positive.");
        }
        this.limitBytes = limitBytes;
        this.reservationTtl = Duration.ofHours(reservationTtlHours);
        this.files = files;
        this.quarantine = quarantine;
        this.lockboxRevisions = lockboxRevisions;
        this.quotaLocks = quotaLocks;
        this.reservations = reservations;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public Reservation reserve(long bytes) {
        if (bytes <= 0) return Reservation.none();

        UUID id = transaction.execute(status -> {
            ensureLockRow();
            quotaLocks.lockById(LOCK_ID).orElseThrow();

            Instant now = Instant.now();
            reservations.deleteExpired(now);
            long used = Math.addExact(
                    Math.addExact(files.sumQuotaBytes(), quarantine.sumQuotaBytes()),
                    lockboxRevisions.sumQuotaBytes()
            );
            long reserved = reservations.sumActiveBytes(now);
            if (bytes > limitBytes || used > limitBytes - bytes || reserved > limitBytes - used - bytes) {
                throw new StorageQuotaExceededException(limitBytes);
            }

            UUID reservationId = UUID.randomUUID();
            reservations.save(new StorageReservation(reservationId, bytes, now.plus(reservationTtl)));
            return reservationId;
        });

        return new Reservation(this, id);
    }

    private void ensureLockRow() {
        if (!quotaLocks.existsById(LOCK_ID)) {
            try {
                quotaLocks.saveAndFlush(new StorageQuotaLock(LOCK_ID));
            } catch (RuntimeException ignored) {
                // Another application instance created the singleton first.
            }
        }
    }

    private void release(UUID id) {
        transaction.executeWithoutResult(status -> reservations.deleteById(id));
    }

    public static final class Reservation implements AutoCloseable {
        private final StorageQuotaService service;
        private final UUID id;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Reservation(StorageQuotaService service, UUID id) {
            this.service = service;
            this.id = id;
        }

        private static Reservation none() {
            return new Reservation(null, null);
        }

        @Override
        public void close() {
            if (service == null || !closed.compareAndSet(false, true)) return;
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        service.release(id);
                    }
                });
            } else {
                service.release(id);
            }
        }
    }
}
