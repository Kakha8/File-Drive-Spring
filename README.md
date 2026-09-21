# File Drive Spring

A self-hosted file drive with a Spring Boot API, React web interface, S3-compatible object storage, malware scanning, and a client-encrypted **Lockbox** mode.

Lockbox is used through the separate [FD-Client](https://github.com/Kakha8/FD-Client) Windows application. The desktop client encrypts, signs, verifies, and decrypts files locally; this backend stores the encrypted artifacts and manages users, devices, revisions, and sharing.

> [!IMPORTANT]
> File Drive and FD-Client are under active development. The Lockbox protocol has not undergone an independent security audit and should not yet be treated as production-ready cryptographic software.

## Repositories

| Repository | Purpose |
| --- | --- |
| **File Drive Spring** (this repository) | REST API, web UI, metadata, authorization, malware scanning, and encrypted object storage |
| [FD-Client](https://github.com/Kakha8/FD-Client) | Windows JavaFX client and native Rust cryptographic core for Lockbox |

## Features

### File drive

- Registration, login, JWT access tokens, and rotating refresh tokens
- FIDO2/WebAuthn second-factor authentication with security-key management
- Two-stage web login and WebAuthn-authorized credential enrollment/removal
- Per-user root folders
- File upload, download, rename, move, copy, and deletion
- Folder creation, browsing, download, rename, move, copy, and deletion
- Favorites, recent files, activity history, and notifications
- Sharing of regular files and folders
- Trash, restore, permanent deletion, and scheduled cleanup
- Text-file preview and editing
- ZIP downloads for multiple items

### Storage and malware protection

- MinIO S3-compatible object storage
- Separate buckets for primary files, trash, quarantine, and Lockbox
- MinIO KES integration and bucket-level server-side encryption
- ClamAV scanning before regular files enter normal storage
- Quarantine records and password-protected ZIP export
- TLS between exposed services

### Lockbox backend

- Device enrollment with registered public encryption and signing keys
- Server-side validation of Lockbox v3 containers, manifests, and signatures
- Storage of encrypted containers and authenticated artifact metadata
- Immutable, hash-linked file revisions
- Historical revision download
- Private encrypted metadata for client-side display
- Revision-specific, read-only sharing with another user or owned device
- Recipient-device key discovery and client-created share envelopes

### Web interface

- React and Vite frontend
- Protected routes and authentication refresh
- Folder navigation and file management
- Upload progress and cancellation
- Recent files, favorites, shared items, trash, activity, and notifications
- Lockbox browsing and device information
- Browser-native security-key/passkey verification during login
- Registered FIDO2 credential status and secure management in account settings

### FIDO2/WebAuthn authentication

File Drive uses password authentication followed by FIDO2/WebAuthn verification.
Accounts can register roaming security keys, such as the ESP32 authenticator, or
platform passkeys supported by the browser and operating system.

```text
Password login
    -> backend validates the username and password
    -> an account without registered credentials receives a normal session
    -> a protected account receives a short-lived, single-use challenge
    -> browser calls navigator.credentials.get()
    -> authenticator asks for user presence or its configured PIN
    -> authenticator signs the challenge with its private credential key
    -> backend verifies the credential, signature, origin, RP ID, and user presence
    -> backend issues a JWT access token and rotating refresh-token cookie
```

The WebAuthn challenge is not an authenticated session. The frontend stores no
access token until the signed assertion succeeds. Requests expire after at most
three minutes, are tied to the account and ceremony purpose, and are consumed on
the first verification attempt. Starting another ceremony of the same type
invalidates the previous one.

Credential enrollment starts from an authenticated account-settings session and
requires the current password. The browser obtains creation options, calls
`navigator.credentials.create()`, and sends the resulting public credential to
the backend. If the account already has a credential, an assertion from an
existing credential must also authorize the new one.

Credential removal requires the current password and a valid assertion from a
registered credential. Successful enrollment or removal revokes existing refresh
sessions. Removing the final credential disables WebAuthn for subsequent logins.

The backend stores credential IDs, public keys, ownership data, display names,
and timestamps. Authenticator private keys never leave the security key or
platform authenticator. Attestation is set to `none`; the server verifies the
ceremony and signature without requiring manufacturer attestation.

> [!WARNING]
> Self-service recovery after losing every registered credential is not implemented. Such an account requires an administrator-assisted recovery process.

## Encryption Model

File Drive uses two separate encryption layers:

1. **Storage encryption** protects MinIO objects at the infrastructure layer through MinIO/KES configuration.
2. **Lockbox client-side encryption** protects file contents before they leave FD-Client.

For Lockbox files, the backend does not receive plaintext, file keys, or private client keys.

```text
Plaintext file
    -> FD-Client encrypts and signs locally
    -> File Drive validates and stores the encrypted artifacts
    -> MinIO stores the encrypted container, manifest, and signature
    -> an enrolled FD-Client downloads and verifies the artifacts
    -> FD-Client decrypts and exports plaintext locally
```

The current desktop implementation uses chunked `CSEMLK03` containers, AES-256-GCM content encryption, ML-KEM-1024 recipient encapsulation, ML-DSA-87 signatures, and Windows DPAPI for local secret protection. See the [FD-Client README](https://github.com/Kakha8/FD-Client#readme) for the complete security model, build instructions, and limitations.

## Technology

| Area | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA |
| Authentication | Password login, FIDO2/WebAuthn verification, JWT access tokens, and rotating refresh tokens |
| Database | File-backed H2 for development |
| Object storage | MinIO Java SDK, MinIO, MinIO KES |
| Malware scanning | ClamAV |
| Web UI | React 19, React Router 7, Vite 8 |
| Lockbox verification | Bouncy Castle and backend protocol validators |
| Desktop client | JavaFX 21 and Rust over JNI in [FD-Client](https://github.com/Kakha8/FD-Client) |
| Local orchestration | Docker Compose |

## Architecture

```text
React web app -----------+
                         |
FD-Client over HTTPS ----+--> Spring Boot REST API
                                  |       |
                                  |       +--> H2 metadata database
                                  |
                                  +--> ClamAV
                                  |
                                  +--> MinIO --> KES
```

The backend follows a conventional layered layout:

```text
controller   REST endpoints
services     application and protocol logic
repository   database access
model        JPA entities
dto          API request and response types
security     authentication and token handling
config       security, storage, and application configuration
```

## Quick Start

### Requirements

- Docker Desktop or Docker Engine with Compose
- Local TLS certificates and keystores in the repository's expected `certs/` paths
- A configured `.env` file containing the required credentials and secrets

### Start the stack

```bash
git clone https://github.com/Kakha8/File-Drive-Spring.git
cd File-Drive-Spring
docker compose up --build
```

The default development endpoints are:

- Web interface: `http://localhost:5173`
- Backend API: `https://localhost:8443`
- MinIO API: `https://localhost:9000`

The MinIO console port is not exposed by the current Compose configuration. Local certificates must be trusted by the browser, Java runtime, or other client connecting to the backend.

## Configuration

The Compose stack reads secrets from `.env` and supplies service-specific defaults. Important variables include:

```dotenv
DB_USERNAME=admin
DB_PASSWORD=change-me
JWT_SECRET=replace-with-a-long-random-secret
TLS_PASSWORD=change-me

MINIO_ROOT_USER=minio-root
MINIO_ROOT_PASSWORD=change-me
MINIO_APP_USER=file-drive-app
MINIO_APP_PASSWORD=change-me
MINIO_KMS_KES_API_KEY=replace-me

S3_LOCKBOX_BUCKET=file-drive-lockbox
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
VITE_API_BASE_URL=https://localhost:8443

WEBAUTHN_ENABLED=true
WEBAUTHN_RP_ID=localhost
WEBAUTHN_ORIGINS=http://localhost:5173
```

Additional backend settings include:

- `JWT_EXPIRES_MINUTES`
- `JPA_DDL_AUTO`
- `S3_QUARANTINE_BUCKET` and `S3_TRASH_BUCKET`
- `LOCKBOX_MAX_CONTAINER_SIZE`, `LOCKBOX_MAX_MANIFEST_SIZE`, and `LOCKBOX_MAX_SIGNATURE_SIZE`
- `CLAMAV_HOST`, `CLAMAV_PORT`, and `CLAMAV_TIMEOUT_MS`
- SSE-C keystore settings prefixed with `S3_SSEC_`

`WEBAUTHN_RP_ID` scopes credentials to the relying-party domain.
`WEBAUTHN_ORIGINS` is a comma-separated list of exact frontend origins without
trailing slashes; it is not the backend API address. Credentials registered for
`localhost` cannot be used for an unrelated deployment domain. Production
deployments should explicitly configure the HTTPS frontend origin and matching
RP domain. Setting `WEBAUTHN_ENABLED=false` blocks WebAuthn ceremonies but does
not downgrade an already-protected account to password-only authentication.

Never use the example credentials in a real deployment. Do not commit `.env`, private keys, keystores, or generated secrets.

## API Overview

All protected routes require an authenticated user. This is a compact overview rather than a complete API reference.

| Area | Base path | Capabilities |
| --- | --- | --- |
| Authentication | `/api/auth` | Password login, WebAuthn challenge completion, refresh, logout, and current-user lookup |
| WebAuthn credentials | `/api/webauthn` | Register, list, and remove FIDO2 security keys and passkeys |
| Registration | `/api/register` | User registration |
| Users | `/api/users` | User lookup, search, creation, and deletion |
| Files | `/api/files` | Upload, download, rename, move, copy, delete, preview, and text updates |
| Folders | `/api/folders` | Browse, create, download, rename, move, copy, and delete |
| Downloads | `/api/download` | Multi-item ZIP downloads |
| Favorites | `/api/favorites` | List, add, and remove favorites |
| Sharing | `/api/share` | Share regular files/folders and list incoming/outgoing shares |
| Trash | `/api/trashcan` | Move, list, restore, permanently delete, and clear |
| Quarantine | `/api/quarantine` | List, inspect, export, and delete quarantined files |
| Activity | `/api/activity` | Activity feed and action types |
| Notifications | `/api/notifications` | List and mark notifications as read |
| Uploads | `/api/uploads` | Cancel an active upload |

### Lockbox API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/lockbox/enrollments` | Begin device enrollment |
| `POST` | `/api/lockbox/enrollments/{enrollmentId}/complete` | Complete enrollment and register public keys |
| `GET` | `/api/lockbox/enrollments/status` | Read Lockbox/device status |
| `GET` | `/api/lockbox/devices` | List enrolled devices owned by the user |
| `POST` | `/api/lockbox/files` | Upload the first encrypted revision |
| `PUT` | `/api/lockbox/files/{fileId}/revisions` | Upload the next revision with expected-revision protection |
| `GET` | `/api/lockbox/files/{fileId}/revisions` | List revision history |
| `GET` | `/api/lockbox/files/{fileId}/revisions/{revision}/{artifact}` | Download a historical container, manifest, or signature |
| `GET` | `/api/lockbox/folders` | View the Lockbox root |
| `GET` | `/api/lockbox/folders/{folderId}` | View a Lockbox folder |
| `GET` | `/api/lockbox/files/private-metadata` | List private encrypted metadata |
| `DELETE` | `/api/lockbox/files/{fileId}` | Delete a Lockbox file and its artifacts |
| `GET` | `/api/lockbox/share-recipients/{username}/keys` | Obtain a recipient's active public keys |
| `POST` | `/api/lockbox/shares` | Store client-created envelopes for a revision |
| `GET` | `/api/lockbox/files/{fileId}/revisions/{revision}/shares` | List recipients of a revision |
| `GET` | `/api/lockbox/shares/received` | List shares available to an enrolled device |
| `GET` | `/api/lockbox/shares/received/{shareUuid}` | Read one received share and its envelope |

For artifact downloads, `{artifact}` is `container`, `manifest`, or `signature`.

### FIDO2/WebAuthn API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/auth/login` | Validate the password and return a session or WebAuthn challenge |
| `POST` | `/api/auth/webauthn/options` | Obtain assertion options for a password-stage challenge |
| `POST` | `/api/auth/webauthn/finish` | Verify the assertion and issue the authenticated session |
| `POST` | `/api/webauthn/registration/options` | Begin credential enrollment with password reauthentication |
| `POST` | `/api/webauthn/registration/finish` | Verify and store a newly created public credential |
| `GET` | `/api/webauthn/credentials` | List safe metadata for the authenticated user's credentials |
| `POST` | `/api/webauthn/credentials/{id}/removal/options` | Begin credential removal with password reauthentication |
| `POST` | `/api/webauthn/credentials/{id}/removal/finish` | Authorize and complete removal with a WebAuthn assertion |

Registration, listing, and removal require an authenticated access token. The
two login assertion endpoints instead use the short-lived password-stage
challenge as authorization. Binary WebAuthn fields are Base64URL encoded in JSON.

## Development

Docker Compose is the recommended development path because the backend depends on MinIO, KES, ClamAV, and local TLS material.

To run only the backend after providing those dependencies and environment variables:

```bash
./mvnw spring-boot:run
```

Run backend tests with:

```bash
./mvnw test
```

Run the web frontend independently with:

```bash
cd frontend
npm install
npm run dev
```

## Current Status and Roadmap

The merged `main` branch includes the Lockbox v3 backend, device enrollment, encrypted revision history, own-device and user sharing, and the matching desktop-client workflows.

Current priorities include:

- More automated coverage for authorization, malformed artifacts, tampering, and concurrent revisions
- Production database migrations and deployment-safe secret management
- Pagination and search for large folders and histories
- Health checks and monitoring for MinIO, KES, and ClamAV
- Lockbox background synchronization and resumable transfers
- Revision conflict handling and selective synchronization
- Device/capability revocation and auditable security events
- Optional hardware-backed key protection and signing
- Administrator-assisted recovery for a lost final FIDO2 credential

## Security

Please do not publish exploitable security findings in a public issue. Contact the repository owner privately with reproduction steps, affected versions, and the expected impact.
