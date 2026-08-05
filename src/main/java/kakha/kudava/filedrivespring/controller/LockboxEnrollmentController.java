package kakha.kudava.filedrivespring.controller;

import kakha.kudava.filedrivespring.dto.LockboxEnrollmentChallengeResponse;
import kakha.kudava.filedrivespring.services.lockbox.LockboxEnrollmentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lockbox/enrollments")
public class LockboxEnrollmentController {

    private final LockboxEnrollmentService enrollmentService;

    public LockboxEnrollmentController(
            LockboxEnrollmentService enrollmentService
    ) {
        this.enrollmentService = enrollmentService;
    }


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LockboxEnrollmentChallengeResponse beginEnrollment() {
        return enrollmentService.beginEnrollment();
    }
}