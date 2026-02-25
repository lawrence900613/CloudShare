# CloudShare
 - CloudShare is a full-stack file sharing platform built for secure upload, preview, download, and expiring share links.
 - This project was built for self-learning and hands-on full-stack practice, covering API design, authentication, cloud file storage, file sharing, testing, and frontend development. 
 - The repository includes both local development setup and production-level deployment setup.
<img width="2547" height="1272" alt="image" src="https://github.com/user-attachments/assets/185286fd-3949-4d87-9e98-c20547c9ffef" />


## Current Stack

- Backend: Java, Spring Boot, Spring Security, JPA/Hibernate
- Frontend: React, Vite
- Database: PostgreSQL for persistent data storage (local container or AWS RDS)
- Object Storage: AWS S3
- Cache/Rate Limit Store: Redis
- Containerization: Docker + Docker Compose
- Reverse Proxy / TLS: Caddy (optional compose profile)

## Core Features

- JWT-based authentication and BCrypt password hashing
- Email verification and password reset via SMTP
- Direct browser-to-S3 upload via presigned URL
- File preview for image / PDF / text-like files
- Expiring share links + revoke support
- Redis-backed API rate limiting (auth/upload/public-share endpoints)

## Feature Breakdown + API Endpoints

### 1) Authentication and account flows
- Register account, login, verify email, resend verification, forgot/reset password.
- Uses JWT for authenticated API access and BCrypt for password hashing.

Endpoints:
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/verify?token=...`
- `POST /api/auth/resend-verification`
- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`

### 2) File management
- Upload file content to S3 and persist file metadata in PostgreSQL, then list, rename, delete, and download owned files.
- Supports direct browser-to-S3 upload through presigned URLs.

Endpoints:
- `POST /api/files/upload/presigned` (direct upload URL)
- `POST /api/files/upload/complete` (finalize metadata after S3 upload)
- `GET /api/files` (list, supports `page` and `size`)
- `GET /api/files/{id}`
- `PATCH /api/files/{id}` (rename/update metadata)
- `DELETE /api/files/{id}`
- `GET /api/files/s3` (list owned S3 objects)
- `DELETE /api/files/s3?key=...`
- `GET /api/files/s3/download?key=...`

### 3) Share links
- Create expiring share links for owned files, list active links, revoke links, and access shared files publicly.

Endpoints:
- `POST /api/shares`
- `GET /api/shares`
- `DELETE /api/shares/{id}`
- `GET /api/shares/public/{token}` (public metadata/preview info)
- `GET /api/shares/public/{token}/download` (public download)
- `GET /s/{token}` (short public download URL)

### 4) Rate limiting and reliability
- Redis-backed rate limiting is applied to sensitive/public endpoints (auth, upload, public share access).
- Limits are configurable from `application.yml` / `application-prod.yml` and env vars.
- Current key strategy uses requester IP and, when available, email identity.

### 5) Deployment and operations
- Dockerized services: backend, frontend, postgres, redis, optional caddy.
- Docker-first deployment setup suitable for EC2, ECS, and EKS with environment-driven configuration.

## Setup

### 1) Redis Rate Limiting

CloudShare uses Redis to store distributed rate-limit counters.

Important env vars:

- `APP_RATE_LIMIT_ENABLED=true`
- `APP_RATE_LIMIT_FAIL_OPEN=false`
- `SPRING_DATA_REDIS_HOST`
- `SPRING_DATA_REDIS_PORT`
- `SPRING_DATA_REDIS_PASSWORD`

Upload limiter is configurable from YAML/env (example):

- `APP_RATE_LIMIT_FILES_UPLOAD_PRESIGNED_LIMIT=3`
- `APP_RATE_LIMIT_FILES_UPLOAD_PRESIGNED_WINDOW=15m`
- `APP_RATE_LIMIT_FILES_UPLOAD_LEGACY_LIMIT=3`
- `APP_RATE_LIMIT_FILES_UPLOAD_LEGACY_WINDOW=15m`

Quick check:

```bash
docker compose exec redis redis-cli ping
```

Expected output: `PONG`

---

### 2) Docker Setup (Local + AWS Deployment)

Service topology used by `compose.yaml`:

- `db`: PostgreSQL (persistent relational data store)
- `redis`: rate-limit store
- `backend`: Spring Boot API
- `frontend`: Nginx serving the Vite build
- `caddy` (optional): reverse proxy + HTTPS with `--profile edge`

Create env files:

```bash
cp .env_example .env
cp frontend/.env.example frontend/.env
```

Set core `.env` values:

- `DOCKER_SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/fileshare`
- `DOCKER_SPRING_DATASOURCE_USERNAME=postgres`
- `DOCKER_SPRING_DATASOURCE_PASSWORD=postgres`
- `APP_JWT_SECRET=<long_random_secret>`
- `APP_AWS_REGION=<region>`
- `APP_AWS_S3_BUCKET=<bucket_name>`
- `APP_AWS_ACCESS_KEY=<access_key>`
- `APP_AWS_SECRET_KEY=<secret_key>`
- `SPRING_MAIL_USERNAME=<smtp_user>`
- `SPRING_MAIL_PASSWORD=<smtp_password_or_app_password>`
- `SPRING_DATA_REDIS_PASSWORD=` (blank if no password)

Run locally:

```bash
docker compose up --build
```

Production note (AWS deployment: EC2/ECS/EKS):

- Use real URLs/domains and production secrets in `.env`.
- Typical production values:
- `SPRING_PROFILES_ACTIVE=prod`
- `SPRING_DATASOURCE_URL=jdbc:postgresql://<rds-endpoint>:5432/<db>`
- `SPRING_DATASOURCE_USERNAME=<rds_user>`
- `SPRING_DATASOURCE_PASSWORD=<rds_password>`
- `SPRING_DATA_REDIS_HOST=<redis-host>`
- `SPRING_DATA_REDIS_PORT=6379`
- `APP_PUBLIC_BASE_URL=https://<backend-domain>`
- `APP_VERIFICATION_BASE_URL=https://<frontend-domain>`
- `APP_PASSWORD_RESET_BASE_URL=https://<frontend-domain>`
- `APP_CORS_ALLOWED_ORIGINS=https://<frontend-domain>,https://www.<frontend-domain>`
- Create `infra/caddy/Caddyfile` with your domain routing before running edge profile.

Deploy (example with Docker host like EC2):

```bash
docker compose up --build -d
docker compose --profile edge up --build -d
```

---

### 3) S3 CORS Requirement (Must include frontend URL)

Because upload is direct from browser to S3, your bucket CORS must allow your frontend origin.

Example:

```json
[
  {
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedOrigins": [
      "http://localhost:5173",
      "https://<your-frontend-domain>"
    ],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3000
  }
]
```

If `AllowedOrigins` does not include your frontend URL, browser upload to S3 will fail.

---

## Project Structure

```text
src/                  Spring Boot backend
frontend/             React frontend
compose.yaml          Multi-service Docker setup
Dockerfile            Backend image
frontend/Dockerfile   Frontend image
```
