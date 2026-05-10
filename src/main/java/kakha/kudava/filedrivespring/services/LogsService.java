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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    private ActionLogs logAction(String actionType,
                                 Long entityId, String entityType,
                                 String detailsJson) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Optional<User> user = userRepository.findByUsername(auth.getName());

        ActionLogs actionLogs = new ActionLogs();
        actionLogs.setAction(ActionType.valueOf(actionType));
        actionLogs.setDetails(null);
        actionLogs.setEntityId(entityId);
        actionLogs.setUser(user.get());
        actionLogs.setEntityType(EntityType.valueOf(entityType));
        actionLogs.setDetails(detailsJson);
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

        ActionLogs actionLogs = logAction(ActionType.DOWNLOAD.name(), parentId,
                entityType, null);
        actionLogsRepository.save(actionLogs);
        log.info(String.format("Logging the download of %s from %s", fileName, parentId));
    }

    public void deleteLog(String fileName, Long parentId, String entityType){
        ActionLogs actionLogs = logAction(ActionType.DELETE.name(), parentId,
                entityType, null);
        actionLogsRepository.save(actionLogs);
        log.info(String.format("Logging the delete of %s", fileName));
    }

    public void renameLog(String fileName, Long parentId,
                          String entityType, String detailsJson){
        ActionLogs actionLogs = logAction(ActionType.RENAME.name(), parentId,
                entityType, detailsJson);
        actionLogsRepository.save(actionLogs);
        log.info(String.format("Logging the rename of %s", fileName));
    }

    public void moveLog(String name, Long entityId, String entityType, String detailsJson){
        ActionLogs actionLogs = logAction(ActionType.MOVE.name(), entityId, entityType, detailsJson);
        actionLogsRepository.save(actionLogs);
        log.info(String.format("Logging the move of %s", name));
    }

    public void copyLog(String name, Long entityId, String entityType, String detailsJson){
        ActionLogs actionLogs = logAction(ActionType.COPY.name(), entityId, entityType, detailsJson);
        actionLogsRepository.save(actionLogs);
        log.info(String.format("Logging the move of %s", name));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void malwareUploadLog(String name, Long parentId, String entityType, String detailsJson) {
        ActionLogs actionLogs = logAction(
                ActionType.MALWARE_UPLOAD.name(),
                parentId,
                entityType,
                detailsJson
        );

        actionLogsRepository.saveAndFlush(actionLogs);
        log.warn("Logging the malware upload of {}", name);
    }

    public void malwareDeleteLog(String name, Long parentId, String entityType) {
        ActionLogs actionLogs = logAction(ActionType.MALWARE_DELETE.name(), parentId, entityType, null);
        actionLogsRepository.save(actionLogs);
        log.info(String.format("Logging the malware delete of {}", name));
    }

    public void malwareScheduleLog(String name, Long parentId, String entityType) {

        ActionLogs actionLogs = new ActionLogs();
        actionLogs.setAction(ActionType.valueOf(ActionType.MALWARE_SCHEDULED_DELETION.name()));
        actionLogs.setDetails(null);
        actionLogs.setEntityId(null);
        actionLogs.setUser(null);
        actionLogs.setEntityType(EntityType.valueOf(entityType));
        actionLogs.setDetails(null);
        log.info(String.format("Logging the scheduled deletion of malware {} from quarantine", name));
        actionLogsRepository.save(actionLogs);
    }
}
