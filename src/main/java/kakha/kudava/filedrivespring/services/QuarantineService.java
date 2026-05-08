package kakha.kudava.filedrivespring.services;

import kakha.kudava.filedrivespring.model.QuarantinedFiles;
import kakha.kudava.filedrivespring.repository.QuarantinedFilesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

}
