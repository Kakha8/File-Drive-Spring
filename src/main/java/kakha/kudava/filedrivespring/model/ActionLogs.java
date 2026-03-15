package kakha.kudava.filedrivespring.model;

import jakarta.persistence.*;
import kakha.kudava.filedrivespring.enums.ActionType;
import kakha.kudava.filedrivespring.enums.EntityType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "action_logs")
public class ActionLogs {

    //Gotta add indexes for quicker lookups

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private ActionType action;

    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    @Column
    private Long entityId;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    @Column
    private String details;

    @Column
    private Long fromFolderId;

    @Column
    private Long toFolderId;
    

}
