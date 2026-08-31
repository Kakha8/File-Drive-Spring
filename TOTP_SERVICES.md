# TOTP service integration

The enrollment service can activate TOTP and persist counters. Enrollment endpoints
are implemented but disabled by default. Two-stage login now enforces `totpEnabled`.
Keep enrollment disabled until the client, recovery flow, and deployment configuration
are ready. These services implement the ESP32 profile:
a 20-byte seed, HMAC-SHA1, six digits, 30 seconds,
and a validation window of the current UTC time step plus/minus one step.

## Deployment configuration

Supply both `TOTP_ENCRYPTION_KEY_ID` (for example `totp-v1`) and
`TOTP_ENCRYPTION_KEY_BASE64` (standard Base64 of 32 cryptographically random bytes)
to the Spring process. Use a dedicated key, not the JWT key or a password. Do not
commit it, put it in the database, or log it. Provision and back it up through your
deployment secret manager. For Docker, explicitly inject these variables into the
app container; a host `.env` alone does not automatically pass them to a container.

If both values are absent, password-only startup remains possible, but encryption
and decryption throw. Partial or malformed configuration fails at construction.
Only one key ID is currently supported: do not replace/remove it while database
records use it. Rotation requires adding a keyring/migration first. Losing the key
means the stored device seeds cannot be recovered.

## Enrollment HTTP API (disabled by default)

Both routes require the existing access-token authentication. Refresh cookies alone
do not authenticate these routes. Requests and responses use `application/json`.

`POST /api/mfa/totp/enrollments` accepts:

```json
{
  "displayName": "My ESP32",
  "secretBase32": "<32-character device seed>",
  "password": "<current password>",
  "existingDeviceId": null,
  "existingCode": null
}
```

For an already-enabled account, supply an ACTIVE device ID and its current code.
Success returns HTTP 201 with `deviceId`, `displayName`, and `expiresAt`.

`POST /api/mfa/totp/enrollments/{deviceId}/confirm` accepts:

```json
{"code":"012345"}
```

Success returns HTTP 200 with `deviceId`, `displayName`, and `confirmedAt`. Neither
endpoint issues tokens or returns the seed. Send codes as strings to preserve zeros.
Expected errors include 400 invalid input/credentials, 401 unauthenticated, 403 invalid
existing-factor authorization, 404 not owned/not found, 409 not pending, 410 expired,
and 429 attempt limits. Responses use the existing API error shape. Malformed JSON
and path conversion errors are sanitized locally so parser messages cannot echo secrets.

`app.totp.enrollment-api-enabled` / `TOTP_ENROLLMENT_API_ENABLED` defaults to false:
authenticated requests receive HTTP 503 without calling the enrollment service.
This is a rollout safeguard, NOT MFA enforcement. Keep it false until client/recovery rollout.
Controllers tests explicitly enable it only against a mocked enrollment service.

## Enrollment service

Call the injected Spring `TotpEnrollmentService` bean, not a manually constructed
instance, so its transaction boundaries and locking apply:

```java
// First factor: existingDeviceId and existingCode are null.
var pending = enrollmentService.begin(displayName, secretBase32, password,
        existingDeviceId, existingCode);
var confirmed = enrollmentService.confirm(pending.deviceId(), newDeviceCode);
```

The owner comes from the authenticated SecurityContext, never a client-supplied user ID.
Begin verifies the password, validates the 32-character Base32 seed, encrypts it, and
creates a five-minute PENDING device. It leaves `totpEnabled` unchanged. For additional
devices on an enabled account, begin also verifies and consumes a code from an ACTIVE
owned device; confirmation checks that authorizing device is still active. Recovery-code
authorization is not implemented. Retrying begin requires a fresh code if the prior one
was consumed; successful start invalidates older pending enrollments, not active devices.

Limits are persisted per account: five starts and five authentication failures per
15-minute fixed window, plus five failed confirmations per enrollment. These survive
new sessions and service restarts. Expired/exhausted pending devices become REVOKED
when checked; a background cleanup task is not included. The limits protect enrollment
only, not the future login endpoints. Add edge/IP limits before public exposure.

Each public operation uses REQUIRES_NEW and locks User before device. Expected
`EnrollmentRejected` exceptions commit attempt counters/expiry changes; infrastructure
exceptions roll back. Confirmation atomically updates ACTIVE status, confirmation time,
matched counter and `totpEnabled`, and reuses `JwtRefreshService.revokeAllForUser`.
It returns no session tokens. Existing access JWTs remain valid until expiry unless a
separate access-token invalidation policy is implemented. All future MFA reset/disable
writers should follow the same User -> device lock order.

Persistence additions: `totp_enrollment_limits` and the TotpDevice columns
`enrollment_failed_attempts` (non-null, default 0) and
`enrollment_authorizing_device_id` (nullable). Development Hibernate schema update can
create these; deployments using managed migrations must add them before rollout.
Response records deliberately contain no seed/ciphertext. Do not log request arguments.
Plaintext byte buffers are cleared after use; the JVM cannot guarantee erasure of
immutable request Strings or internal cryptographic-provider copies.

AES-GCM authenticates a domain/version marker, account public UUID, and encryption
key ID. Moving encrypted seeds to a different account fails decryption. This does
not bind ciphertext to an individual device row within the same account.

## Login integration (implemented)

See `TWO_STAGE_LOGIN.md` for the HTTP contract, transaction guarantees and rollout notes.
The verifier itself is stateless; `TwoStageLoginService` locks the account/devices,
consumes counters/challenges and issues refresh sessions atomically. Controllers write
tokens only after the service transaction commits. Missing devices/keys never cause
password-only fallback for an enabled account.

Recovery codes, protected disable/device-management flows, cleanup of historical
challenges, and edge/IP abuse limits remain to be implemented. Do not log plaintext
seeds, codes, provisioning requests, or decrypted buffers.

## Tests

Run `mvn test "-Dtest=Totp*Tests"`.
Controller tests cover real SecurityConfig authentication, request mapping, response
redaction, malformed requests, service status propagation, and default-disabled routes.
Enrollment tests use real H2 transactions to check activation, ownership, expiry,
persisted attempt limits, concurrent confirmation, additional-factor step-up and
rollback of both activation and refresh revocation on infrastructure failure.
Tests cover AES-GCM round trips, nonce freshness, tampering, account binding, wrong
keys, configuration failures, Base32 input, ASCII/leading-zero codes, step boundaries,
and supplied replay counters. RFC 6238 Appendix B SHA1 vectors are reduced to six
digits for this device profile: https://www.rfc-editor.org/rfc/rfc6238.html#appendix-B
