# File Drive Spring

A self-hosted secure file drive built with **Spring Boot**, **React**, **MinIO**, **MinIO KES**, and **ClamAV**, with a companion Windows desktop client for end-to-end encrypted storage.

The project provides a REST API for user authentication, folder/file management, object storage, malware scanning, quarantine handling, encrypted S3-compatible storage, and **Lockbox** client-side encrypted files. A React web frontend is included, while the separate [FD-Client](https://github.com/Kakha8/FD-Client) desktop application provides the Lockbox GUI and performs encryption, signing, verification, and decryption on the user's device.

> [!IMPORTANT]
> This project is under active development and has not undergone an independent security audit. The Lockbox formats and APIs may change and should not yet be treated as production-ready cryptographic software.

---

## Project Status

This project is actively being developed and refactored.

Current focus:

- Hardening the backend file-drive API
- Building out the React frontend
- Developing the Lockbox backend protocol and the [FD-Client](https://github.com/Kakha8/FD-Client) desktop GUI
- Expanding encrypted revision, device enrollment, and sharing workflows

The backend is the most complete part of this repository. The React frontend and the separate desktop client are both under active development.

---

## Features

### Authentication and Users

- User registration and login
- Spring Security integration
- JWT access tokens
- Refresh-token flow using secure HttpOnly cookies
- Refresh-token rotation
- Logout with refresh-token revocation
- Per-user root folders

### File and Folder Management

- Upload files
- Download files
- Delete files
- Create folders
- View folder contents
- Rename files and folders
- Move files and folders
- Copy files and folders
- Store file metadata in the database
- Store file objects in MinIO

### Object Storage

- MinIO S3-compatible object storage
- Main storage bucket for user files
- Separate quarantine bucket for infected files
- Dockerized MinIO setup
- Automatic bucket initialization through `minio-init`

### Encryption

- Server-side S3 encryption using **MinIO KES**
- KES-backed master key configuration
- MinIO bucket encryption setup during container initialization
- TLS configuration for backend and MinIO/KES communication
- Lockbox storage for client-encrypted containers, signed manifests, and signatures
- Device enrollment and public-key registration for Lockbox clients
- Immutable, hash-linked encrypted file revisions
- Revision-specific sharing with registered recipient devices

The two encryption layers serve different purposes: MinIO/KES protects stored objects at the infrastructure layer, while FD-Client's Lockbox mode encrypts files before upload so the backend never receives plaintext content or private client keys.

### Lockbox Desktop Client

The Windows desktop GUI lives in the separate [FD-Client repository](https://github.com/Kakha8/FD-Client). It currently includes:

- JavaFX interface backed by a native Rust cryptographic core
- Chunked `CSEMLK03` containers for streaming large files
- AES-256-GCM content encryption
- ML-KEM-1024 recipient key encapsulation
- ML-DSA-87 artifact and share signatures
- Windows DPAPI protection for device secrets and refresh tokens
- Authenticated download, decryption, and plaintext export
- Immutable revision history and historical revision export
- Read-only sharing of a specific revision with another user or owned device
- Upload/download progress and cancellation

See the [FD-Client README](https://github.com/Kakha8/FD-Client#readme) for its security model, requirements, build instructions, and current limitations.

### Malware Scanning and Quarantine

- ClamAV integration
- Files are scanned before normal storage
- Infected files are moved into a quarantine bucket
- Quarantined file metadata is stored separately
- Quarantined files can be viewed through API endpoints
- Quarantined files can be downloaded as password-protected ZIP files
- Quarantined files can be deleted and are automatically deleted after 30 days

### Logging

- Action logging for important file operations
- Tracks events such as upload, download, delete, rename, move, copy, and malware-related actions

### Frontend

- React + Vite frontend included
- Login page
- Protected app routing
- Folder browsing UI
- File upload flow
- File preview/download flow
- Frontend token refresh integration

The frontend is still under development and does not yet represent the final UI/UX of the project.

---

## Tech Stack

### Backend

- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA
- JWT
- H2 Database
- MinIO Java SDK
- ClamAV
- Zip4j
- Docker

### Frontend

- React
- Vite
- React Router
- JavaScript

### Desktop Client

- Java 21 and JavaFX 21
- Rust 2024 native cryptographic library exposed through JNI
- Windows DPAPI
- Maven and Cargo

### Infrastructure

- Docker Compose
- MinIO
- MinIO KES
- ClamAV
- TLS certificates / keystores

---

## Architecture

The backend follows a layered structure:

```text
controller   -> REST API endpoints
services     -> business logic
repository   -> database access
model        -> JPA entities
dto          -> request/response objects
security     -> authentication and token handling
config       -> application, security, and storage configuration
```

Files are stored in MinIO, while metadata such as file names, folder relationships, users, refresh tokens, logs, and quarantine records are stored in the database.

Lockbox adds a client-encrypted path to this architecture:

```text
plaintext file
    -> FD-Client encrypts and signs locally
    -> backend validates and stores encrypted artifacts
    -> MinIO stores the encrypted container, manifest, and signature
    -> an enrolled FD-Client downloads, verifies, and decrypts locally
```

The backend manages authorization, enrolled devices, public keys, revision metadata, and shares. Plaintext, file keys, and private encryption/signing keys remain on client devices.

---

## Docker Services

The Docker setup includes:

| Service | Description | Default Port |
| --- | --- | --- |
| Backend | Spring Boot REST API | `8443` |
| Frontend | React/Vite development frontend | `5173` |
| MinIO | S3-compatible object storage API | `9000` |
| MinIO Console | MinIO web console | `9001` |
| KES | MinIO Key Encryption Service | `7373` |
| ClamAV | Malware scanning service | `3310` internal |
| minio-init | Initializes buckets and encryption | one-time container |

---

## Quick Start with Docker

Clone the repository:

```bash
git clone <your-repo-url>
cd File-Drive-Spring
```

Start the stack:

```bash
docker compose up --build
```

Default local services:

- Backend API: `https://localhost:8443`
- Frontend: `http://localhost:5173`
- MinIO API: `https://localhost:9000`
- MinIO Console: `https://localhost:9001`

Because the project uses local TLS certificates, your browser or HTTP client may warn that the certificate is not trusted. For local development, you may need to trust the local certificate or disable certificate verification in your API client.

---

## Environment Variables

The project uses environment variables for secrets and service configuration.

Common variables include:

```env
DB_USERNAME=admin
DB_PASSWORD=admin
JWT_SECRET=change-me
JWT_REFRESH_DAYS=7
TLS_PASSWORD=change-me

S3_ENDPOINT=https://minio:9000
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=minioadmin123
S3_BUCKET=file-drive-bucket
S3_QUARANTINE_BUCKET=file-drive-quarantine

CLAMAV_HOST=clamav
CLAMAV_PORT=3310
```

For real deployments, replace all default credentials and secrets.

---

## API Overview

### Authentication

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/api/auth/login` | Log in and receive an access token |
| `POST` | `/api/auth/refresh` | Rotate refresh token and receive a new access token |
| `POST` | `/api/auth/logout` | Revoke refresh tokens and clear refresh cookie |
| `GET` | `/api/auth/me` | Check current authenticated user |

### Users

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/users` | List users |
| `GET` | `/api/users/{id}` | Get user by ID |
| `POST` | `/api/users` | Create user |
| `DELETE` | `/api/users/{id}` | Delete user |

### Folders

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/folders/root` | View current user's root folder |
| `GET` | `/api/folders/{id}` | View folder contents |
| `POST` | `/api/folders` | Create folder |
| `DELETE` | `/api/folders/{id}` | Delete folder |
| `PUT` | `/api/folders/{id}/rename` | Rename folder |
| `PUT` | `/api/folders/{id}/move` | Move folder |
| `PUT` | `/api/folders/{id}/copy` | Copy folder |

### Files

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/api/files` | Upload file |
| `GET` | `/api/files/{id}` | Download file |
| `DELETE` | `/api/files/{id}` | Delete file |
| `PUT` | `/api/files/{id}/rename` | Rename file |
| `PUT` | `/api/files/{id}/move` | Move file |
| `PUT` | `/api/files/{id}/copy` | Copy file |

### Quarantine

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/quarantine` | List quarantined files |
| `GET` | `/api/quarantine/{id}` | View quarantined file metadata |
| `GET` | `/api/quarantine/{id}/download` | Download quarantined file as password-protected ZIP |
| `DELETE` | `/api/quarantine/{id}` | Delete quarantined file |

### Lockbox

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/api/lockbox/enrollments` | Begin enrollment of a Lockbox device |
| `POST` | `/api/lockbox/enrollments/{enrollmentId}/complete` | Complete device enrollment and register public keys |
| `GET` | `/api/lockbox/enrollments/status` | Get Lockbox status for a device |
| `POST` | `/api/lockbox/files` | Upload an encrypted container, manifest, and signature |
| `PUT` | `/api/lockbox/files/{fileId}/revisions` | Upload the next encrypted file revision |
| `GET` | `/api/lockbox/files/{fileId}/revisions` | List immutable revision history |
| `GET` | `/api/lockbox/folders` | View the Lockbox root folder |
| `GET` | `/api/lockbox/folders/{folderId}` | View a Lockbox folder |
| `GET` | `/api/lockbox/files/private-metadata` | List encrypted private metadata artifacts |
| `DELETE` | `/api/lockbox/files/{fileId}` | Delete a Lockbox file and its stored artifacts |
| `GET` | `/api/lockbox/devices` | List the current user's enrolled devices |
| `GET` | `/api/lockbox/share-recipients/{username}/keys` | Get a recipient's active public encryption keys |
| `POST` | `/api/lockbox/shares` | Publish client-created envelopes for a specific revision |
| `GET` | `/api/lockbox/shares/received` | List shares received by an enrolled device |

---

## Running Without Docker

Docker is the recommended way to run the project because the backend depends on MinIO, KES, certificates, and ClamAV.

For manual local development, you need to provide:

- A running MinIO instance
- A configured KES instance if using KES encryption
- A running ClamAV daemon
- Required environment variables
- TLS keystore/truststore files
- A configured database connection

Then run:

```bash
./mvnw spring-boot:run
```

---

## Roadmap

### Backend

- Improve authorization rules for file and folder ownership
- Add better validation and error responses
- Add pagination/search for large folders
- Add more tests for file operations, auth, quarantine, and encryption flows
- Improve production-readiness of configuration and secret handling
- Add monitoring/health checks for MinIO, KES, and ClamAV

### Frontend

- Finish React file-manager UI
- Improve upload progress and error handling
- Add better folder navigation
- Add quarantine/admin views
- Improve authentication state handling
- Polish responsive layout and user experience

### Lockbox and Desktop Client

- Background synchronization between registered devices and encrypted web storage
- File-system change detection and resumable transfers
- Revision-aware conflict detection and resolution
- Selective synchronization for chosen files and folders
- Device and capability revocation with an auditable event history
- Optional ESP32 hardware-backed signing, key protection, and TOTP authentication

---


## Why This Project

This project was built to practice and demonstrate a more realistic backend system than a simple CRUD application.

It combines:

- REST API design
- Spring Boot architecture
- Authentication and refresh-token handling
- S3-compatible object storage
- Server-side encryption
- Malware scanning
- Quarantine workflows
- Dockerized infrastructure
- Frontend integration
- Client-side encrypted Lockbox storage
- Device enrollment, encrypted revisions, and revision-specific sharing
- JavaFX and native Rust desktop-client integration

---

## Notes

The project is evolving quickly, so some implementation details may change as the backend, web frontend, and [FD-Client](https://github.com/Kakha8/FD-Client) desktop application continue to improve.
