package kakha.kudava.filedrivespring.services.lockbox;

import kakha.kudava.filedrivespring.exceptions.LockboxApiException;
import kakha.kudava.filedrivespring.model.*;
import kakha.kudava.filedrivespring.repository.*;
import kakha.kudava.filedrivespring.services.ResourceAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LockboxRevisionSharesServiceTests {
    @Test void ownerReceivesOrdinaryAndDeviceTargetedSelfSharesOnceInStableRepositoryOrder(){
        Fixture f=new Fixture();User alice=user(2L,"alice"),owner=f.owner;UUID deviceId=UUID.randomUUID();
        LockboxShare ordinary=share(owner,alice,UUID.randomUUID(),null);
        LockboxShare duplicateOrdinary=share(owner,alice,UUID.randomUUID(),Instant.ofEpochSecond(1_900_000_000L));
        LockboxShare self=share(owner,owner,deviceId,Instant.ofEpochSecond(1_900_000_001L));
        when(f.shares.findActiveRevisionSharesForOwner(eq(70L),eq(1L),eq(LockboxShare.Status.ACTIVE),any()))
                .thenReturn(List.of(ordinary,duplicateOrdinary,self));
        var response=f.service.revisionShares(10L,7);
        assertEquals(10L,response.fileId());assertEquals(7,response.revision());assertEquals(2,response.shares().size());
        assertEquals("alice",response.shares().get(0).recipientUsername());assertNull(response.shares().get(0).targetDeviceId());assertEquals(0,response.shares().get(0).expiresAtUnixSeconds());
        assertEquals("owner",response.shares().get(1).recipientUsername());assertEquals(deviceId,response.shares().get(1).targetDeviceId());assertEquals(1_900_000_001L,response.shares().get(1).expiresAtUnixSeconds());
    }
    @Test void unauthorizedTombstonedAndForeignRevisionUseNonDisclosingNotFound(){
        Fixture unauthorized=new Fixture();when(unauthorized.files.findByIdAndProfileUserId(10L,1L)).thenReturn(Optional.empty());assertNotFound(assertThrows(LockboxApiException.class,()->unauthorized.service.revisionShares(10L,7)));
        Fixture deleted=new Fixture();when(deleted.metadata.isDeleted()).thenReturn(true);assertNotFound(assertThrows(LockboxApiException.class,()->deleted.service.revisionShares(10L,7)));
        Fixture foreignRevision=new Fixture();when(foreignRevision.revisions.findByLockboxFileIdAndRevision(10L,8L)).thenReturn(Optional.empty());assertNotFound(assertThrows(LockboxApiException.class,()->foreignRevision.service.revisionShares(10L,8)));
    }
    @Test void noActiveSharesReturnsImmutableEmptyArray(){Fixture f=new Fixture();when(f.shares.findActiveRevisionSharesForOwner(eq(70L),eq(1L),eq(LockboxShare.Status.ACTIVE),any())).thenReturn(List.of());var response=f.service.revisionShares(10L,7);assertTrue(response.shares().isEmpty());assertThrows(UnsupportedOperationException.class,()->response.shares().add(null));}
    private static void assertNotFound(LockboxApiException e){assertEquals(HttpStatus.NOT_FOUND,e.getStatus());assertEquals("LOCKBOX_FILE_NOT_FOUND",e.getCode());}
    private static User user(long id,String username){User user=mock(User.class);when(user.getId()).thenReturn(id);when(user.getUsername()).thenReturn(username);return user;}
    private static LockboxShare share(User owner,User recipient,UUID deviceId,Instant expiry){LockboxShare share=mock(LockboxShare.class);LockboxDevice device=mock(LockboxDevice.class);when(share.getOwner()).thenReturn(owner);when(share.getRecipient()).thenReturn(recipient);when(share.getTargetDevice()).thenReturn(device);when(device.getDeviceUuid()).thenReturn(deviceId);when(share.getExpiresAt()).thenReturn(expiry);return share;}
    private static final class Fixture{
        final UserRepository users=mock(UserRepository.class);final LockboxKeyRepository keys=mock(LockboxKeyRepository.class);final ResourceAccessService access=mock(ResourceAccessService.class);final LockboxFileRepository files=mock(LockboxFileRepository.class);final LockboxFileRevisionRepository revisions=mock(LockboxFileRevisionRepository.class);final LockboxShareRepository shares=mock(LockboxShareRepository.class);final LockboxDeviceRepository devices=mock(LockboxDeviceRepository.class);final User owner=user(1L,"owner");final LockboxFile file=mock(LockboxFile.class);final LockboxFileRevision revision=mock(LockboxFileRevision.class);final FileMetaData metadata=mock(FileMetaData.class);
        final LockboxSharingService service=new LockboxSharingService(users,keys,access,files,revisions,shares,mock(LockboxShareEnvelopeRepository.class),mock(LockboxShareEnvelopeParser.class),mock(LockboxSignatureVerifier.class),mock(LockboxObjectStorage.class),devices);
        Fixture(){when(access.currentUser()).thenReturn(owner);when(files.findByIdAndProfileUserId(10L,1L)).thenReturn(Optional.of(file));when(file.getId()).thenReturn(10L);when(file.getFile()).thenReturn(metadata);when(revisions.findByLockboxFileIdAndRevision(10L,7L)).thenReturn(Optional.of(revision));when(revision.getId()).thenReturn(70L);}
    }
}
