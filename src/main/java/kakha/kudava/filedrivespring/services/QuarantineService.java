package kakha.kudava.filedrivespring.services;

import kakha.kudava.filedrivespring.dto.ViewQuarantinedFilesDTO;
import kakha.kudava.filedrivespring.model.QuarantinedFiles;
import kakha.kudava.filedrivespring.repository.QuarantinedFilesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuarantineService {

    private final QuarantinedFilesRepository quarantinedFilesRepository;

    public QuarantineService(QuarantinedFilesRepository quarantinedFilesRepository) {
        this.quarantinedFilesRepository = quarantinedFilesRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public QuarantinedFiles save(QuarantinedFiles quarantinedFile) {
        return quarantinedFilesRepository.saveAndFlush(quarantinedFile);
    }

    @Transactional(readOnly = true)
    public List<ViewQuarantinedFilesDTO> viewQuarantine() {
        return quarantinedFilesRepository.findAll()
                .stream()
                .map(q -> new ViewQuarantinedFilesDTO(
                        q.getId(),
                        q.getOriginalFilename(),
                        q.getObjectKey(),
                        q.getContentType(),
                        q.getSize(),
                        q.getChecksum(),
                        q.getClamAvResponse(),
                        q.getParentFolderId(),
                        q.getUser().getId(),
                        q.getUser().getUsername(),
                        q.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ViewQuarantinedFilesDTO findQuarantinedFileById(Long id) {
        QuarantinedFiles q = quarantinedFilesRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Quarantined file not found: " + id));

        return new ViewQuarantinedFilesDTO(
                q.getId(),
                q.getOriginalFilename(),
                q.getObjectKey(),
                q.getContentType(),
                q.getSize(),
                q.getChecksum(),
                q.getClamAvResponse(),
                q.getParentFolderId(),
                q.getUser().getId(),
                q.getUser().getUsername(),
                q.getCreatedAt()
        );
    }
}
