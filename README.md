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
- Optional TOTP two-factor authentication with hardware-device enrollment
- Two-stage web login and authenticated device status/removal
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
- TOTP challenge entry during login
- TOTP enabled/disabled status, enrolled hardware-device listing, and secure removal in account settings

### TOTP two-factor authentication

TOTP is optional per account. Accounts without an active TOTP device continue to use the normal password login. Enrollment is currently performed through the client and the web interface handles login verification and device management.

The hardware device generates the TOTP seed. The client sends that seed to the authenticated enrollment endpoint over TLS, and the backend stores it encrypted with AES-256-GCM. The encryption key is supplied externally and is never stored in the database. The server must be able to decrypt the seed temporarily because TOTP verification requires the original shared secret; a one-way password hash cannot be used for this purpose.

```text
Hardware device generates seed
    -> authenticated client begins enrollment over HTTPS
    -> backend encrypts the seed and creates a pending device
    -> client submits a fresh TOTP code
    -> backend confirms the device and enables TOTP for the account

Password login
    -> account without TOTP: issue the normal session
    -> account with TOTP: issue a short-lived, single-use MFA challenge
    -> web client submits challenge token + six-digit code
    -> backend verifies the code and issues the normal session
```

TOTP codes have replay protection and failed enrollment/login/removal attempts are rate-limited. A challenge is not an authenticated session, and the web client does not store an access token until the second stage succeeds.

Device removal requires the current password and a fresh TOTP code. If multiple active devices exist, a different active device must authorize the removal. If the final device is removed, TOTP is disabled for the account. Removal marks the record `REVOKED` instead of deleting its history and revokes the account's refresh tokens. Existing access tokens remain valid until their normal expiration.

> [!WARNING]
> Lost-device recovery and recovery codes are not implemented yet. A user who loses their only active device cannot complete TOTP login or remove it through the normal flow. Email-based recovery is planned; do not rely on this implementation for production account recovery until that path exists.

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
| Authentication | JWT access tokens, rotating refresh tokens, and optional TOTP 2FA |
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

# Optional TOTP enrollment and server-side seed encryption
TOTP_ENROLLMENT_API_ENABLED=true
TOTP_ENCRYPTION_KEY_ID=totp-v1
TOTP_ENCRYPTION_KEY_BASE64=replace-with-a-random-base64-encoded-32-byte-key
```

Additional backend settings include:

- `JWT_EXPIRES_MINUTES`
- `JPA_DDL_AUTO`
- `S3_QUARANTINE_BUCKET` and `S3_TRASH_BUCKET`
- `LOCKBOX_MAX_CONTAINER_SIZE`, `LOCKBOX_MAX_MANIFEST_SIZE`, and `LOCKBOX_MAX_SIGNATURE_SIZE`
- `CLAMAV_HOST`, `CLAMAV_PORT`, and `CLAMAV_TIMEOUT_MS`
- SSE-C keystore settings prefixed with `S3_SSEC_`

`TOTP_ENROLLMENT_API_ENABLED` controls whether new enrollment requests are accepted. `TOTP_ENCRYPTION_KEY_ID` is a non-secret identifier used to track the active encryption key. `TOTP_ENCRYPTION_KEY_BASE64` is the secret 32-byte AES key used to encrypt TOTP seeds at rest.

Generate a development key with a cryptographically secure generator, for example:

```bash
openssl rand -base64 32
```

Preserve both the encryption key and its ID while enrolled devices exist. Replacing either value without migrating or re-enrolling devices makes their stored seeds undecryptable. In production, supply the key through a secrets manager rather than committing it to source control or an image.

Never use the example credentials in a real deployment. Do not commit `.env`, private keys, keystores, or generated secrets.

## API Overview

All protected routes require an authenticated user. This is a compact overview rather than a complete API reference.

| Area | Base path | Capabilities |
| --- | --- | --- |
| Authentication | `/api/auth` | Password login, TOTP login verification, refresh, logout, and current-user lookup |
| TOTP devices | `/api/mfa/totp/enrollments` | Enroll, confirm, list status/devices, and revoke TOTP hardware devices |
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

### TOTP API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/auth/login` | Perform the password stage; returns a normal session or an MFA challenge |
| `POST` | `/api/auth/mfa/totp` | Exchange a challenge token and fresh TOTP code for a normal session |
| `POST` | `/api/mfa/totp/enrollments` | Begin authenticated hardware-device enrollment |
| `POST` | `/api/mfa/totp/enrollments/{deviceId}/confirm` | Confirm a pending enrollment with a fresh code |
| `GET` | `/api/mfa/totp/enrollments/status` | Return enabled status and safe summaries of active devices |
| `DELETE` | `/api/mfa/totp/enrollments/devices/{deviceId}` | Revoke an active device after password and TOTP step-up verification |

Enrollment, status, and removal routes require a normal authenticated session. API responses expose device IDs and display names but never return plaintext seeds, encrypted seed bytes, encryption nonces, passwords, or submitted TOTP codes.

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
- TOTP recovery codes and verified-email recovery for a lost final device

## Security

Please do not publish exploitable security findings in a public issue. Contact the repository owner privately with reproduction steps, affected versions, and the expected impact.
