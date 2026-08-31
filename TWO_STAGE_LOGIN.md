# Two-stage login

## Client contract

Send the existing JSON request to `POST /api/auth/login` over HTTPS:

```json
{"username":"alice","password":"<password>"}
```

For users without TOTP, HTTP 200 still returns the existing `LoginResponse` fields:
`accessToken`, `userId`, `username`, `publicUuid`, plus the HttpOnly refresh cookie.

For users with TOTP, HTTP 200 instead returns:

```json
{
  "mfaRequired": true,
  "challengeToken": "<opaque random token>",
  "expiresAt": "<UTC expiry>"
}
```

No access token or new refresh session is issued at this stage. An empty, expired
refresh cookie clears any old browser/client cookie. Keep the challenge only in
memory, do not activate the account UI, and prompt for a six-digit code.

Send `POST /api/auth/mfa/totp` with:

```json
{"challengeToken":"<token from password step>","code":"012345"}
```

This endpoint does not require a JWT: the opaque challenge is the restricted proof of
password verification. Successful verification returns the ordinary `LoginResponse`
and refresh cookie. Codes are strings, including leading zeros. No device ID is
required; the server checks only ACTIVE factors belonging to the challenge's account.

Invalid credentials, expired/replaced/consumed challenges, and invalid/replayed codes
return 401. Account-level limits return 429. Malformed JSON returns a sanitized 400.
Do not handle a 401 from these login endpoints by automatically attempting a refresh.
Start again after expiry; starting again replaces the outstanding challenge. A new
password step does not clear the failure budget. Keep the seed on the physical device,
not in the client. Normal session restoration still uses `/api/auth/refresh`.

## Backend guarantees

- Challenges contain 32 random bytes; only SHA-256 hashes are persisted. They expire
  after three minutes and cannot be parsed as access JWTs or used as refresh tokens.
- Password and MFA failures share a per-account, persisted budget of ten failures
  per 15-minute fixed window. Challenge creation is also limited to ten per window.
  Unknown-account password checks perform a dummy password hash comparison, but still
  require edge/IP throttling; this is not a comprehensive anti-abuse system.
- Account -> challenge/device locks serialize verification with enrollment. Counter
  consumption, challenge consumption, and refresh creation commit together before a
  controller returns tokens. Infrastructure failures roll the entire operation back.
- MFA challenge completion rejects accounts whose password changed or TOTP was disabled.
- The existing refresh service now rotates tokens transactionally under account ->
  refresh locks. Reuse of the old refresh token fails; enrollment/revocation uses the
  same account lock. Refresh tokens record whether MFA completed. A password-only or
  legacy refresh token cannot refresh an account that now requires TOTP.
- Chosen access-session policy: already-issued access JWTs remain valid until their
  original expiration, including after TOTP activation or refresh-token revocation.
  Revocation never extends their expiry. No session-version field or JWT denylist is
  used. A stolen access JWT therefore remains usable for its remaining lifetime.
  Once it expires, a revoked refresh session cannot renew it; a fresh login must
  complete the account's required factors. Absolute session/MFA reauthentication-age
  limits are not implemented here.
- The password check uses the configured PasswordEncoder against the locked User record;
  the current User model has no disabled/locked-account fields. Add those checks here
  if account lifecycle flags are introduced later.

## Deployment and remaining work

New tables: `mfa_login_challenges`, `login_attempt_limits`. New column on
`jwt_refresher`: `mfa_verified BOOLEAN NOT NULL DEFAULT FALSE`. Existing sessions
default to not MFA-verified. Development uses Hibernate schema update; production
managed migrations must apply these schema changes before rollout. New account-owned
tables also need to be considered in any account-deletion workflow.

Keep `TOTP_ENROLLMENT_API_ENABLED=false` until JavaFX/web clients can handle both login
responses, encryption keys are configured, and recovery/device-removal flows exist.
No client/archive files were changed. Do not enable TOTP on accounts without a working
second-stage UI. Secrets, passwords, codes, and challenge/refresh tokens must not be logged.

Recovery codes, device listing/removal/disable, expired-record cleanup, and edge/IP
throttling remain separate backend work. Future MFA-disable/removal flows must reuse
the account-lock/revocation protocol while retaining the chosen JWT-expiry policy.

Tests: `mvn test "-Dtest=TwoStageLogin*Tests,AuthenticationPublicUuidContractTests,Totp*Tests"`.
