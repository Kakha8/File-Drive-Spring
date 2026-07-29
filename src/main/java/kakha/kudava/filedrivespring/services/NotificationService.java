package kakha.kudava.filedrivespring.services;

import jakarta.persistence.EntityNotFoundException;
import kakha.kudava.filedrivespring.enums.EntityType;
import kakha.kudava.filedrivespring.enums.NotificationType;
import kakha.kudava.filedrivespring.enums.SharingRole;
import kakha.kudava.filedrivespring.model.FileMetaData;
import kakha.kudava.filedrivespring.model.Folders;
import kakha.kudava.filedrivespring.model.Notification;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(
            NotificationRepository notificationRepository
    ) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public Notification createNotification(
            User recipient,
            User actor,
            NotificationType type,
            String title,
            String message,
            EntityType entityType,
            Long entityId
    ) {
        Notification notification = new Notification(
                recipient,
                actor,
                type,
                title,
                message,
                entityType,
                entityId
        );

        return notificationRepository.save(notification);
    }

    @Transactional
    public Notification createNotification(
            User recipient,
            User actor,
            NotificationType type,
            String title,
            String message
    ) {
        return createNotification(
                recipient,
                actor,
                type,
                title,
                message,
                null,
                null
        );
    }

    @Transactional
    public Notification notifyUploadCompleted(
            User recipient,
            FileMetaData file
    ) {
        return createNotification(
                recipient,
                null,
                NotificationType.UPLOAD_COMPLETED,
                "Upload complete",
                quote(file.getFileName())
                        + " was uploaded successfully.",
                EntityType.FILE,
                file.getId()
        );
    }

    @Transactional
    public Notification notifyMalwareDetected(
            User recipient,
            String fileName
    ) {
        return createNotification(
                recipient,
                null,
                NotificationType.MALWARE_DETECTED,
                "Malware detected",
                quote(fileName)
                        + " was blocked because malware was detected."
        );
    }

    @Transactional
    public Notification notifyFileShared(
            User recipient,
            User actor,
            FileMetaData file,
            SharingRole role
    ) {
        return createNotification(
                recipient,
                actor,
                NotificationType.FILE_SHARED,
                "File shared with you",
                actor.getUsername()
                        + " shared "
                        + quote(file.getFileName())
                        + " with you as "
                        + roleLabel(role)
                        + ".",
                EntityType.FILE,
                file.getId()
        );
    }

    @Transactional
    public Notification notifyFolderShared(
            User recipient,
            User actor,
            Folders folder,
            SharingRole role
    ) {
        return createNotification(
                recipient,
                actor,
                NotificationType.FOLDER_SHARED,
                "Folder shared with you",
                actor.getUsername()
                        + " shared "
                        + quote(folder.getName())
                        + " with you as "
                        + roleLabel(role)
                        + ".",
                EntityType.FOLDER,
                folder.getId()
        );
    }

    @Transactional
    public Notification notifyAccessRevoked(
            User recipient,
            User actor,
            EntityType entityType,
            Long entityId,
            String entityName
    ) {
        String resourceLabel =
                entityType == EntityType.FOLDER
                        ? "folder"
                        : "file";

        return createNotification(
                recipient,
                actor,
                NotificationType.ACCESS_REVOKED,
                "Access revoked",
                actor.getUsername()
                        + " revoked your access to the "
                        + resourceLabel
                        + " "
                        + quote(entityName)
                        + ".",
                entityType,
                entityId
        );
    }

    public Page<Notification> getNotifications(
            User recipient,
            Pageable pageable
    ) {
        return notificationRepository
                .findAllByRecipientAndRemovedAtIsNullOrderByCreatedAtDesc(
                        recipient,
                        pageable
                );
    }

    public long getUnreadCount(User recipient) {
        return notificationRepository
                .countByRecipientAndReadAtIsNullAndRemovedAtIsNull(
                        recipient
                );
    }

    @Transactional
    public Notification markRead(
            Long notificationId,
            User recipient
    ) {
        Notification notification =
                getOwnedActiveNotification(
                        notificationId,
                        recipient
                );

        notification.markRead();

        return notification;
    }

    @Transactional
    public Notification markUnread(
            Long notificationId,
            User recipient
    ) {
        Notification notification =
                getOwnedActiveNotification(
                        notificationId,
                        recipient
                );

        notification.markUnread();

        return notification;
    }

    @Transactional
    public int markAllRead(User recipient) {
        return notificationRepository.markAllReadByRecipient(
                recipient,
                Instant.now()
        );
    }

    @Transactional
    public void removeNotification(
            Long notificationId,
            User recipient
    ) {
        Notification notification =
                getOwnedActiveNotification(
                        notificationId,
                        recipient
                );

        notification.remove();
    }

    private Notification getOwnedActiveNotification(
            Long notificationId,
            User recipient
    ) {
        return notificationRepository
                .findByIdAndRecipientAndRemovedAtIsNull(
                        notificationId,
                        recipient
                )
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Notification not found"
                        )
                );
    }

    private String roleLabel(SharingRole role) {
        SharingRole safeRole =
                role == null
                        ? SharingRole.VIEWER
                        : role;

        return safeRole
                .name()
                .toLowerCase(Locale.ROOT);
    }

    private String quote(String value) {
        String safeValue =
                value == null || value.isBlank()
                        ? "Untitled"
                        : value.trim();

        return "\"" + safeValue + "\"";
    }
}