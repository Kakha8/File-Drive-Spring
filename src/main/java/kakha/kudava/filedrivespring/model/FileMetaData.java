package kakha.kudava.filedrivespring.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "files")
public class FileMetaData {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false, unique = true)
    private String objectKey;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String objectType;

    @Column(nullable = false)
    private Instant creationDate = Instant.now();

    @Column(nullable = false)
    private boolean deleted;

}
