package kakha.kudava.filedrivespring.services;

import kakha.kudava.filedrivespring.exceptions.StorageQuotaExceededException;
import kakha.kudava.filedrivespring.model.StorageQuotaLock;
import kakha.kudava.filedrivespring.repository.FileMetaDataRepository;
import kakha.kudava.filedrivespring.repository.LockboxFileRevisionRepository;
import kakha.kudava.filedrivespring.repository.QuarantinedFilesRepository;
import kakha.kudava.filedrivespring.repository.StorageQuotaLockRepository;
import kakha.kudava.filedrivespring.repository.StorageReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageQuotaServiceTests {
    private final FileMetaDataRepository files = mock(FileMetaDataRepository.class);
    private final QuarantinedFilesRepository quarantine = mock(QuarantinedFilesRepository.class);
    private final LockboxFileRevisionRepository lockbox = mock(LockboxFileRevisionRepository.class);
    private final StorageQuotaLockRepository locks = mock(StorageQuotaLockRepository.class);
    private final StorageReservationRepository reservations = mock(StorageReservationRepository.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private StorageQuotaService service;

    @BeforeEach
    void setUp() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(locks.existsById(1L)).thenReturn(true);
        when(locks.lockById(1L)).thenReturn(Optional.of(new StorageQuotaLock(1L)));
        when(files.sumQuotaBytes()).thenReturn(70L);
        when(quarantine.sumQuotaBytes()).thenReturn(10L);
        when(lockbox.sumQuotaBytes()).thenReturn(5L);
        when(reservations.sumActiveBytes(any(Instant.class))).thenReturn(4L);
        service = new StorageQuotaService(100L, 24L, files, quarantine, lockbox, locks, reservations, transactions);
    }

    @Test
    void reservesAndReleasesBytesWithinTheLimit() {
        try (StorageQuotaService.Reservation ignored = service.reserve(10L)) {
            verify(reservations).save(any());
        }

        verify(reservations).deleteById(any());
    }

    @Test
    void rejectsWritesThatWouldExceedTheGlobalLimit() {
        assertThrows(StorageQuotaExceededException.class, () -> service.reserve(12L));
        verify(reservations, never()).save(any());
    }
}
