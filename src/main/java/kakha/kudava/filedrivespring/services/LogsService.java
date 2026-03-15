package kakha.kudava.filedrivespring.services;

import kakha.kudava.filedrivespring.enums.ActionType;
import kakha.kudava.filedrivespring.enums.EntityType;
import kakha.kudava.filedrivespring.model.ActionLogs;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.ActionLogsRepository;
import kakha.kudava.filedrivespring.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class LogsService {

    private final ActionLogsRepository actionLogsRepository;
    private final UserRepository userRepository;
    public LogsService(ActionLogsRepository actionLogsRepository, UserRepository userRepository) {
        this.actionLogsRepository = actionLogsRepository;
        this.userRepository = userRepository;
    }

    private ActionLogs logAction(String action, String actionType,
                                 Long parentId, String entityType) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Optional<User> user = userRepository.findByUsername(auth.getName());

        ActionLogs actionLogs = new ActionLogs();
        actionLogs.setAction(ActionType.valueOf(actionType));
        actionLogs.setDetails(null);
        actionLogs.setEntityId(parentId);
        actionLogs.setUser(user.get());
        actionLogs.setEntityType(EntityType.valueOf(entityType));
        return actionLogs;
    }
    public void uploadLog(String fileName, Long parentId, String entityType){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Optional<User> user = userRepository.findByUsername(auth.getName());

        ActionLogs actionLogs = new ActionLogs();
        actionLogs.setAction(ActionType.valueOf("UPLOAD"));
        actionLogs.setDetails(null);
        actionLogs.setEntityId(parentId);
        actionLogs.setUser(user.get());
        actionLogs.setEntityType(EntityType.valueOf(entityType));
        actionLogsRepository.save(actionLogs);
        log.info(String.format("Logging the upload of %s to %s", fileName, parentId));

    }

    public void downloadLog(String fileName, Long parentId, String entityType){

        ActionLogs actionLogs = logAction(ActionType.DOWNLOAD.name(), ActionType.DOWNLOAD.name(), parentId, entityType);
        actionLogsRepository.save(actionLogs);
        log.info(String.format("Logging the download of %s to %s", fileName, parentId));
    }

}
