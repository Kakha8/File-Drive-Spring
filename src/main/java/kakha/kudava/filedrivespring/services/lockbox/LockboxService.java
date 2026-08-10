package kakha.kudava.filedrivespring.services.lockbox;

import kakha.kudava.filedrivespring.dto.*;
import kakha.kudava.filedrivespring.enums.DriveSpace;
import kakha.kudava.filedrivespring.exceptions.LockboxApiException;
import kakha.kudava.filedrivespring.model.*;
import kakha.kudava.filedrivespring.records.LockboxDownloadResult;
import kakha.kudava.filedrivespring.repository.*;
import kakha.kudava.filedrivespring.services.ResourceAccessService;
import kakha.kudava.filedrivespring.services.objects.RootFolderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service
public class LockboxService {
    private static final byte[] SIGNING_DOMAIN="FD-LOCKBOX-MANIFEST-V1\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private final LockboxManifestParser manifests; private final LockboxSignatureRecordParser signatures;
    private final LockboxV3ContainerValidator containers; private final LockboxSignatureVerifier verifier;
    private final LockboxObjectStorage storage; private final FileMetaDataRepository files; private final FolderRepository folders;
    private final LockboxFileRepository lockboxFiles; private final LockboxKeyRepository keys; private final LockboxProfileRepository profiles;
    private final RootFolderService roots; private final ResourceAccessService access;
    private final long maxContainer,maxManifest,maxSignature;

    public LockboxService(LockboxManifestParser manifests,LockboxSignatureRecordParser signatures,
      LockboxV3ContainerValidator containers,LockboxSignatureVerifier verifier,LockboxObjectStorage storage,
      FileMetaDataRepository files,FolderRepository folders,LockboxFileRepository lockboxFiles,
      LockboxKeyRepository keys,LockboxProfileRepository profiles,RootFolderService roots,ResourceAccessService access,
      @Value("${lockbox.upload.max-container-size}") long maxContainer,
      @Value("${lockbox.upload.max-manifest-size}") long maxManifest,
      @Value("${lockbox.upload.max-signature-size}") long maxSignature){
        this.manifests=manifests;this.signatures=signatures;this.containers=containers;this.verifier=verifier;this.storage=storage;
        this.files=files;this.folders=folders;this.lockboxFiles=lockboxFiles;this.keys=keys;this.profiles=profiles;this.roots=roots;this.access=access;
        this.maxContainer=maxContainer;this.maxManifest=maxManifest;this.maxSignature=maxSignature;
    }

    @Transactional(rollbackFor=Exception.class)
    public LockboxUploadResponse upload(MultipartFile container,MultipartFile manifest,MultipartFile signature,Long parentId) throws Exception {
        User user=access.currentUser();
        LockboxProfile profile=profiles.findByUserId(user.getId()).filter(p->p.getStatus()==LockboxProfile.Status.ENABLED)
          .orElseThrow(()->new LockboxApiException("LOCKBOX_NOT_ENABLED",HttpStatus.FORBIDDEN,"Lockbox is not enabled."));
        Folders parent=resolveParent(parentId,user);
        Path dir=Files.createTempDirectory("lockbox-v3-"); Path c=dir.resolve("container"),m=dir.resolve("manifest"),s=dir.resolve("signature");
        List<String> uploaded=new ArrayList<>();
        try{
            stage(container,c,maxContainer);stage(manifest,m,maxManifest);stage(signature,s,maxSignature);
            byte[] manifestBytes=readExact(m,maxManifest,"INVALID_MANIFEST");
            LockboxSignatureRecordParser.SignatureRecord sig=signatures.parse(readExact(s,maxSignature,"INVALID_SIGNATURE_RECORD"));
            LockboxKey signing=findKey(profile,LockboxKey.Role.SIGNING,sig.signingKeyId(),"UNKNOWN_SIGNING_KEY");
            LockboxDevice device=signing.getDevice();
            if(device.getStatus()!=LockboxDevice.Status.ACTIVE) throw LockboxApiException.bad("DEVICE_NOT_ACTIVE","Signing device is not active.");
            byte[] transcript=new byte[SIGNING_DOMAIN.length+manifestBytes.length];System.arraycopy(SIGNING_DOMAIN,0,transcript,0,SIGNING_DOMAIN.length);System.arraycopy(manifestBytes,0,transcript,SIGNING_DOMAIN.length,manifestBytes.length);
            try{verifier.verify(signing.getPublicKey(),transcript,sig.signature());}catch(RuntimeException e){throw LockboxApiException.bad("INVALID_SIGNATURE","Manifest signature is invalid.");}
            LockboxManifestParser.Manifest man=manifests.parse(manifestBytes);
            if(!MessageDigest.isEqual(sig.signingKeyId(),man.signingKeyId())) throw LockboxApiException.bad("INVALID_SIGNATURE","Signing key IDs do not match.");
            if(!device.getDeviceUuid().equals(man.deviceUuid())) throw LockboxApiException.bad("DEVICE_NOT_ACTIVE","Manifest device is invalid.");
            if(man.revision()!=1||!allZero(man.previousManifestHash())) throw LockboxApiException.bad("UNSUPPORTED_REVISION","Only initial revision 1 is supported.");
            validateNames(container,manifest,signature,man.clientFileId());
            long size=Files.size(c);if(size!=man.containerSize())throw LockboxApiException.bad("CONTAINER_SIZE_MISMATCH","Container size does not match the signed manifest.");
            byte[] hash=digest(c);if(!MessageDigest.isEqual(hash,man.containerHash()))throw LockboxApiException.bad("CONTAINER_HASH_MISMATCH","Container hash does not match the signed manifest.");
            LockboxV3ContainerValidator.Container parsed=containers.validate(c,size);
            if(!parsed.clientFileId().equals(man.clientFileId())||parsed.suiteId()!=man.suiteId()||!MessageDigest.isEqual(parsed.encryptionKeyId(),man.encryptionKeyId()))
                throw LockboxApiException.bad("MANIFEST_CONTAINER_MISMATCH","Container public fields do not match the signed manifest.");
            findKey(profile,LockboxKey.Role.ENCRYPTION,man.encryptionKeyId(),"UNKNOWN_ENCRYPTION_KEY");
            if(lockboxFiles.existsByProfileIdAndClientFileIdAndRevision(profile.getId(),man.clientFileId(),man.revision())) duplicate();
            String prefix="users/"+user.getId()+"/lockbox/"+man.clientFileId()+"/1/requests/"+UUID.randomUUID()+"/";
            String ck=prefix+"container.fdcse",mk=prefix+"manifest.fdmanifest",sk=prefix+"signature.fdsig";
            registerRollbackCleanup(uploaded);
            upload(ck,c,LockboxObjectStorage.ArtifactType.CONTAINER,uploaded);upload(mk,m,LockboxObjectStorage.ArtifactType.MANIFEST,uploaded);upload(sk,s,LockboxObjectStorage.ArtifactType.SIGNATURE,uploaded);
            FileMetaData meta=new FileMetaData();meta.setObjectKey(ck);meta.setFileName(man.clientFileId()+".fdcse");meta.setObjectType(LockboxObjectStorage.ArtifactType.CONTAINER.contentType());meta.setChecksum(HexFormat.of().formatHex(hash));meta.setCreationDate(Instant.now());meta.setSize(size);meta.setOwner(user);meta.setParent(parent);meta.setDriveSpace(DriveSpace.LOCKBOX);
            FileMetaData saved=files.saveAndFlush(meta);
            LockboxFile lf=new LockboxFile(saved,profile,man.clientFileId(),man.revision(),3,1,size,hash,man.encryptionKeyId(),man.signingKeyId(),man.deviceUuid(),parsed.chunkSize(),parsed.chunkCount(),ck,mk,sk);
            try{lockboxFiles.saveAndFlush(lf);}catch(DataIntegrityViolationException e){duplicate();}
            return new LockboxUploadResponse(saved.getId(),man.clientFileId(),man.revision(),parent.getId(),size,HexFormat.of().formatHex(hash),3,1,lf.getCreatedAt());
        }catch(Exception e){if(!TransactionSynchronizationManager.isSynchronizationActive())cleanup(uploaded,e);throw e;}
        finally{deleteTree(dir);}
    }

    @Transactional(readOnly=true) public LockboxDownloadResult openDownload(Long id,LockboxObjectStorage.ArtifactType type)throws Exception{
        User user=access.currentUser();LockboxFile f=lockboxFiles.findByIdAndProfileUserId(id,user.getId()).orElseThrow(()->new LockboxApiException("LOCKBOX_FILE_NOT_FOUND",HttpStatus.NOT_FOUND,"Lockbox file not found."));
        String base=f.getClientFileId().toString();String key,name;long size;
        switch(type){case CONTAINER->{key=f.getContainerObjectKey();name=base+".fdcse";size=f.getContainerSize();}case MANIFEST->{key=f.getManifestObjectKey();name=base+".fdmanifest";size=LockboxManifestParser.LENGTH;}default->{key=f.getSignatureObjectKey();name=base+".fdsig";size=LockboxSignatureRecordParser.LENGTH;}}
        return new LockboxDownloadResult(name,size,type.contentType(),storage.download(key));
    }
    @Transactional(readOnly=true) public LockboxFolderViewResponse viewRoot(){User u=access.currentUser();return view(roots.ensureLockboxRootFolder(u),u);}
    @Transactional(readOnly=true) public LockboxFolderViewResponse viewFolder(Long id){User u=access.currentUser();return view(ownedFolder(id,u),u);}
    private LockboxFolderViewResponse view(Folders parent,User user){
        List<LockboxFolderItemResponse> ds=folders.findFoldersByParent_Id(parent.getId()).stream().filter(x->x.getDriveSpace()==DriveSpace.LOCKBOX&&!x.isDeleted()&&!x.isPermanentlyDeleted()&&owned(x,user)).map(x->new LockboxFolderItemResponse(x.getId(),x.getName(),parent.getId())).toList();
        List<FileMetaData> fs=files.findByParent_IdAndDeletedFalse(parent.getId()).stream().filter(x->x.getDriveSpace()==DriveSpace.LOCKBOX&&!x.isPermanentlyDeleted()&&Objects.equals(x.getOwner().getId(),user.getId())).toList();
        Map<Long,LockboxFile> map=new HashMap<>();lockboxFiles.findAllById(fs.stream().map(FileMetaData::getId).toList()).forEach(x->map.put(x.getId(),x));
        List<LockboxFileItemResponse> items=fs.stream().map(x->{LockboxFile l=map.get(x.getId());if(l==null)throw new IllegalStateException("Lockbox metadata is missing.");return new LockboxFileItemResponse(l.getId(),l.getClientFileId(),l.getRevision(),l.getContainerSize(),l.getCreatedAt(),l.getFormatVersion(),l.getSuiteId());}).toList();
        return new LockboxFolderViewResponse(parent.getId(),parent.getName(),parent.getParent()==null?null:parent.getParent().getId(),ds,items);
    }
    private LockboxKey findKey(LockboxProfile p,LockboxKey.Role role,byte[] id,String code){return keys.findAllByDeviceProfileIdAndRoleAndStatus(p.getId(),role,LockboxKey.Status.ACTIVE).stream().filter(k->MessageDigest.isEqual(k.getKeyId(),id)).findFirst().orElseThrow(()->LockboxApiException.bad(code,"Registered active key was not found."));}
    private Folders resolveParent(Long id,User u){return id==null?roots.ensureLockboxRootFolder(u):ownedFolder(id,u);}
    private Folders ownedFolder(Long id,User u){Folders f=access.requireFolderOwner(id);if(f.getDriveSpace()!=DriveSpace.LOCKBOX||f.isDeleted()||f.isPermanentlyDeleted()||!owned(f,u))throw LockboxApiException.bad("INVALID_LOCKBOX_FOLDER","Destination Lockbox folder is invalid.");return f;}
    private boolean owned(Folders f,User u){return f.getOwner()!=null&&Objects.equals(f.getOwner().getId(),u.getId());}
    private static void stage(MultipartFile part,Path path,long max)throws IOException{if(part==null||part.isEmpty())throw LockboxApiException.bad("INVALID_MANIFEST","All three artifacts are required.");try(InputStream in=part.getInputStream();OutputStream out=Files.newOutputStream(path)){byte[] buf=new byte[65536];long total=0;for(int n;(n=in.read(buf))!=-1;){total=Math.addExact(total,n);if(total>max)throw LockboxApiException.bad("ARTIFACT_TOO_LARGE","Lockbox artifact exceeds its configured limit.");out.write(buf,0,n);}}}
    private static byte[] readExact(Path p,long max,String code)throws IOException{long n=Files.size(p);if(n>max||n>Integer.MAX_VALUE)throw LockboxApiException.bad("ARTIFACT_TOO_LARGE","Lockbox artifact exceeds its configured limit.");return Files.readAllBytes(p);}
    private static byte[] digest(Path p)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA3-512");try(InputStream in=Files.newInputStream(p)){byte[] b=new byte[1024*1024];for(int n;(n=in.read(b))!=-1;)d.update(b,0,n);}return d.digest();}
    private void upload(String k,Path p,LockboxObjectStorage.ArtifactType t,List<String> uploaded)throws Exception{storage.upload(k,p,t);uploaded.add(k);}
    private void registerRollbackCleanup(List<String> uploaded){TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)cleanup(List.copyOf(uploaded),null);}});}
    private void cleanup(List<String> ks,Exception original){for(String k:ks)try{storage.delete(k);}catch(Exception x){if(original!=null)original.addSuppressed(x);}}
    private static void deleteTree(Path d){if(d==null)return;try(var paths=Files.walk(d)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}catch(IOException ignored){}}
    private static boolean allZero(byte[] b){int x=0;for(byte v:b)x|=v;return x==0;}
    private static void duplicate(){throw new LockboxApiException("DUPLICATE_LOCKBOX_FILE",HttpStatus.CONFLICT,"This Lockbox file revision already exists.");}
    private static void validateNames(MultipartFile c,MultipartFile m,MultipartFile s,UUID id){checkName(c,id+".fdcse");checkName(m,id+".fdmanifest");checkName(s,id+".fdsig");}
    private static void checkName(MultipartFile p,String expected){String n=p.getOriginalFilename();if(n!=null&&!n.isBlank()&&!n.equalsIgnoreCase(expected))throw LockboxApiException.bad("INVALID_MANIFEST","Multipart artifact filename does not match the signed file UUID.");}
}
