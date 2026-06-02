package kakha.kudava.filedrivespring.controller;

import jakarta.servlet.http.HttpServletResponse;
import kakha.kudava.filedrivespring.dto.ViewTrashcanDTO;
import kakha.kudava.filedrivespring.services.TrashcanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trashcan")
public class TrashcanRestController {

    private final TrashcanService trashcanService;

    public TrashcanRestController(TrashcanService trashcanService) {
        this.trashcanService = trashcanService;
    }

    @GetMapping()
    public ViewTrashcanDTO trashcan() {
        ViewTrashcanDTO dto = trashcanService.viewTrashcan();
        return dto;
    }

}
