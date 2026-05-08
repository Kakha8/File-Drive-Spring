package kakha.kudava.filedrivespring.controller;

import kakha.kudava.filedrivespring.dto.ViewQuarantinedFilesDTO;
import kakha.kudava.filedrivespring.model.QuarantinedFiles;
import kakha.kudava.filedrivespring.services.QuarantineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/quarantine")
public class QuarantineRestController {

    private final QuarantineService quarantineService;

    public QuarantineRestController(QuarantineService quarantineService) {
        this.quarantineService = quarantineService;
    }

    @GetMapping
    public List<ViewQuarantinedFilesDTO> viewQuarantine() {
        return quarantineService.viewQuarantine();
    }

    @GetMapping("/{id}")
    public ViewQuarantinedFilesDTO findQuarantinedFileById(@PathVariable Long id) {
        return quarantineService.findQuarantinedFileById(id);
    }

}
