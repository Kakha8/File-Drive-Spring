package kakha.kudava.filedrivespring.dto.lockbox;
import java.util.List;
public record LockboxRevisionSharesResponse(Long fileId,long revision,List<LockboxRevisionShareRecipientResponse> shares){public LockboxRevisionSharesResponse{shares=shares==null?List.of():List.copyOf(shares);}}
