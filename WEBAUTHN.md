# WebAuthn backend API

This implements password + WebAuthn authentication. It does not implement browser UI, a desktop OAuth handoff, or ESP32 CTAP2 firmware.

## Configuration

Defaults match the existing local frontend:

```properties
WEBAUTHN_ENABLED=true
WEBAUTHN_RP_ID=localhost
WEBAUTHN_ORIGINS=http://localhost:5173
```

These environment variables are read by Spring. Origins are exact comma-separated browser origins (no trailing slash), not the API address. For deployment, configure HTTPS origins and the RP domain explicitly. Credentials registered for localhost cannot be used for an unrelated deployment domain. Setting enabled=false blocks WebAuthn operations but does NOT downgrade already-enrolled accounts to password-only login.

The public key model has no signature counter, per project choice. Library counter validation is explicitly disabled. Signature, challenge, RP ID, origin, credential ownership and user-presence checks remain enabled. User verification is preferred, not mandatory: this flow already requires the account password and authenticator possession. A PIN-only local UI must not falsely claim a CTAP user-verification result.

## Registration

Both routes require an access-token Authorization header. The account comes exclusively from the authenticated principal.

1. POST `/api/webauthn/registration/options`:

```json
{"password":"current account password","displayName":"My ESP32","existingDeviceId":null,"existingCode":null}
```

For a TOTP-only account, supply an active TOTP device ID and a fresh code. The code is consumed. For an account with WebAuthn already enabled, the response includes a separate WebAuthn assertion request authorizing the additional credential. Password reauthentication is always required.

Response:

```json
{"requestId":"uuid","expiresAt":"ISO-8601","publicKey":{},"authorizationPublicKey":null}
```

`publicKey` is the encoded creation options. Decode Base64URL buffer fields before calling `navigator.credentials.create({publicKey})`, or use a WebAuthn JSON helper. If `authorizationPublicKey` is present, also call `navigator.credentials.get()` with those options to prove possession of an already registered key. The two requests have distinct challenges.

2. POST `/api/webauthn/registration/finish`:

```json
{"requestId":"uuid","credential":{},"authorizationCredential":null}
```

`credential` is the JSON-encoded registration result (including id, rawId, type, response, clientExtensionResults). Use Base64URL for binary fields. `authorizationCredential` is the assertion result for the existing key when requested. Do not send JSON.stringify directly on an unconverted browser PublicKeyCredential object.

Success: HTTP 201 with `{ "credentialRecordId": 123, "displayName": "My ESP32" }`. This activates WebAuthn, invalidates outstanding password-login challenges, and revokes existing refresh sessions. The frontend should return to login. Already-issued access tokens retain their existing short lifetime; this change does not add access-token revocation.

## Login

1. POST the existing `/api/auth/login` with username/password.
2. A WebAuthn-enabled account returns `{ "mfaRequired":true, "method":"webauthn", "challengeToken":"...", "expiresAt":"..." }`, with no session token. Existing TOTP accounts return method `totp`.
3. POST `/api/auth/webauthn/options` with `{ "challengeToken":"..." }`.
4. Decode its `publicKey` options and call `navigator.credentials.get({publicKey})`.
5. POST `/api/auth/webauthn/finish` with `{ "challengeToken":"...", "requestId":"uuid", "credential":{} }`.

Successful verification returns the existing LoginResponse access token and sets the existing HttpOnly/Secure refresh cookie. The two WebAuthn login endpoints do not require a JWT: the password-step challenge is their authorization. They are not passwordless endpoints.

WebAuthn-enabled accounts cannot use `/api/auth/mfa/totp` to bypass the security key. Refresh issuance also checks the new account flag. The current frontend/FD-Client must handle method=webauthn before enrolling accounts through this API; their old TOTP screen cannot complete this flow.

## Expiry and storage

Requests are stored server-side, expire after at most three minutes, and are tied to account, purpose, password fingerprint, and (for login) the password-step challenge. Starting another request of the same kind replaces the previous one. Submitted requests are single-use even on verification failure. After a failed login assertion, start again with the password step. Serializing finishes under a database account lock prevents parallel reuse.

Tables: `webauthn_credentials`, `webauthn_ceremonies`; account flag: `users.webauthn_enabled`. Your development ddl-auto=update creates them at startup. Tests use isolated in-memory H2, not the live database. Deployments using schema validation must provide equivalent migrations.

Credential registration validates with Yubico java-webauthn-server 2.9.0. No requests are sent to Yubico. Attestation preference is none; this accepts compliant authenticators without requiring manufacturer certification. A browser virtual authenticator is suitable for development. Real hardware/browser end-to-end testing remains required.

## Credential management

`GET /api/webauthn/credentials` lists the authenticated user's registered credentials with their display name, creation time, and last-use time.

Removal is a two-step authenticated ceremony. Start it with `POST /api/webauthn/credentials/{id}/removal/options`, supplying the account password and, optionally, an active TOTP device ID and fresh code. Without TOTP authorization, the response contains a WebAuthn assertion challenge that any registered credential on the account may sign. Complete removal with `POST /api/webauthn/credentials/{id}/removal/finish` and the returned request ID plus that assertion. TOTP authorization permits removal of a credential whose physical key was lost or erased. Successful removal revokes refresh sessions and disables WebAuthn when no credentials remain.
