package kakha.kudava.filedrivespring.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "sharing_permissions")
public class SharingPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Folders folder;

    @ManyToOne
    private FileMetaData file;

    @ManyToOne
    private User owner;

    @ManyToOne
    private User sharedWith;

    @ManyToOne
    private User sharedBy;

    @Enumerated(EnumType.STRING)
    private SharingRole role;

    private boolean active = true;

    private Instant createdAt;

    private Instant revokedAt;
}