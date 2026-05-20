# File Drive Spring

A self-hosted secure file drive built with **Spring Boot**, **React**, **MinIO**, **MinIO KES**, and **ClamAV**.

The project provides a REST API for user authentication, folder/file management, object storage, malware scanning, quarantine handling, and encrypted S3-compatible storage. A React frontend is included and currently under active development.

---

## Project Status

This project is actively being developed and refactored.

Current focus:

- Hardening the backend file-drive API
- Building out the React frontend
- Preparing the project for a future desktop GUI app

The backend is the most complete part of the project. The React frontend exists, but should still be considered a work in progress.

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

> Note: current encryption is server-side object-storage encryption. Client-side encryption is planned for the future desktop GUI app.

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

### Desktop GUI App

A future goal is to build a desktop GUI app with two vault modes:

- **Local vault**: files stored locally on the user's machine
- **Remote vault**: files stored through the File Drive backend

Planned GUI security features:

- Optional client-side encryption
- Local encryption keys controlled by the user
- Ability to choose between normal remote storage and encrypted vault storage

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
- Future desktop vault design

---

## Notes

The project is evolving quickly, so some implementation details may change as the backend, frontend, and planned desktop GUI app continue to improve.
