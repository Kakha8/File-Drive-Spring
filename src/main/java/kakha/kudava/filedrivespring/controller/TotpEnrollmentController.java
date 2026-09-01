package kakha.kudava.filedrivespring.controller;

import kakha.kudava.filedrivespring.dto.totp.TotpEnrollmentConfirmRequest;
import kakha.kudava.filedrivespring.dto.totp.TotpEnrollmentRequest;
import kakha.kudava.filedrivespring.records.ApiErrorResponse;
import kakha.kudava.filedrivespring.services.totp.TotpEnrollmentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** Authenticated routes; SecurityConfig's anyRequest().authenticated() protects these. */
@RestController
@RequestMapping(value = "/api/mfa/totp/enrollments", produces = MediaType.APPLICATION_JSON_VALUE)
public class TotpEnrollmentController {
    private final TotpEnrollmentService enrollment;
    private final boolean enabled;

    public TotpEnrollmentController(TotpEnrollmentService enrollment,
            @Value("${app.totp.enrollment-api-enabled:false}") boolean enabled) {
        this.enrollment = enrollment;
        this.enabled = enabled;
    }

    @GetMapping("/status")
    public ResponseEntity<TotpEnrollmentService.Status> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(enrollment.status());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TotpEnrollmentService.Enrollment> begin(@RequestBody TotpEnrollmentRequest request) {
        requireEnabled();
        var result = enrollment.begin(request.displayName(), request.secretBase32(), request.password(),
                request.existingDeviceId(), request.existingCode());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(result);
    }

    @PostMapping(value = "/{deviceId}/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TotpEnrollmentService.Confirmation> confirm(
            @PathVariable Long deviceId, @RequestBody TotpEnrollmentConfirmRequest request) {
        requireEnabled();
        if (deviceId < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid enrollment ID.");
        var result = enrollment.confirm(deviceId, request.code());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    // Jackson/path errors can contain submitted passwords or seeds. Never echo their messages.
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> malformedRequest(Exception ignored) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(ApiErrorResponse.of("INVALID_ENROLLMENT_REQUEST", "Invalid enrollment request.", 400));
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TOTP enrollment is not available.");
        }
    }
}
