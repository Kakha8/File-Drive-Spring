# File Drive Spring

A Spring Boot backend for a file drive system with user management, file/folder operations, authentication, and MinIO object storage.

---

## Overview

This project is a backend-focused file storage system that allows users to upload, manage, and organize files.

It was initially started before my Java bootcamp and is currently being refactored to improve architecture, structure, and scalability.

The goal is to build a more realistic system than a simple CRUD app by integrating storage, authentication, and future multi-protocol access.

---

## Quick Start (Docker)

The easiest way to run the project:

```bash
git clone <your-repo-url>
cd File-Drive-Spring
docker-compose up --build
```

### Services

* Backend: http://localhost:8080
* MinIO API: http://localhost:9000
* MinIO Console: http://localhost:9001

Default MinIO credentials:

* Username: minioadmin
* Password: minioadmin

---

## Features

* User registration and login
* JWT authentication + refresh tokens
* File upload and management
* Folder creation, rename, move
* File rename and move
* Object storage using MinIO (S3-compatible)
* Dockerized setup
* Layered architecture (Controller, Service, Repository)

---

## Planned Features

* SFTP access using **SFTPGo**
* React SPA frontend
* Encryption (SSE-KMS / HashiCorp Vault)
* Improved authorization
* Better logging and monitoring

---

## Tech Stack

* Java 21
* Spring Boot
* Spring Security
* Spring Data JPA
* JWT
* H2 Database
* MinIO
* Docker / Docker Compose

---

## Architecture

The project follows a layered structure:

* `controller` → REST endpoints
* `services` → business logic
* `repository` → data access
* `model` → entities
* `dto` → request/response objects
* `security` → authentication & tokens
* `config` → configuration

Files are stored in MinIO, while metadata is stored in the database.

---

## SFTP (Planned)

SFTP is planned using SFTPGo.

The idea is to:

* provide access via clients like FileZilla
* use MinIO as the storage backend
* separate application logic from file transfer layer

---

## Running Without Docker (Optional)

1. Start MinIO manually
2. Set environment variables:

```bash
export DB_USERNAME=admin
export DB_PASSWORD=admin
export JWT_SECRET=secret
```

3. Run the app:

```bash
./mvnw spring-boot:run
```

---

## Project Status

Currently being refactored and improved.

Recent focus:

* cleaner architecture
* MinIO integration
* Docker setup

---

## Why This Project

This project was built to practice:

* Spring Boot architecture
* REST API design
* authentication with JWT
* object storage integration
* backend system design

---

## Future Goals

* complete backend refactor
* integrate SFTPGo
* build React frontend
* add encryption support
* improve production readiness

---

## Notes

This project is actively developed and may change as improvements are made.
