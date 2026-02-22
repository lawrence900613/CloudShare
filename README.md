# CloudShare

CloudShare is a full-stack file storage app:
- Spring Boot backend
- React frontend
- AWS S3 for file storage

This project was built for self-learning and hands-on practice across a full-stack workflow, including API design, authentication, cloud file storage, sharing, testing, and frontend development.
The repo includes local development setup for both backend and frontend.

## What it does

- User registration and login
- Email verification flow (token by email)
- Forgot password flow (email reset link + token-based reset)
- Upload, list, download, and delete files
- Direct browser-to-S3 uploads using presigned URLs
- Rename file in cloud (s3) with metadata/share-link rekey
- File previews in the UI (image, PDF, text-like files)
- Create and revoke share links

Sample UI
<img width="2265" height="1242" alt="image" src="https://github.com/user-attachments/assets/7e3402a4-c2fb-4019-9200-a0d2d39769b6" />


## Feature breakdown

### 1. File upload, download, and preview
- Files are uploaded directly from browser to S3 using presigned PUT URLs.
- Backend finalizes uploads by validating object ownership and saving metadata.
- Users can only access their own files and view basic metadata such as file size and uploaded time.
- Preview is supported in the dashboard for image, PDF, and text-like files.
- Download and delete actions are permission-scoped to file ownership.

### 1.1 Direct upload flow (presigned URL)
1. `POST /api/files/upload/presigned` returns `{ s3Key, uploadUrl, method }`.
2. Frontend uploads bytes directly to S3 using the returned URL.
3. `POST /api/files/upload/complete` persists metadata after S3 object verification.

### 1.2 Real cloud rename
- `PATCH /api/files/{id}` performs S3 rename via copy + delete.
- File metadata (`s3Key`, `originalName`) is updated after successful S3 operation.
- Share links pointing to the old key are rekeyed to the new key.

### 2. Share links
- Users can create share links from existing uploaded files.
- Share links can be listed with metadata such as status and expiration.
- Links can be revoked to immediately stop access.
- This flow separates private file ownership from controlled sharing access.

### 3. Authentication and secure access
- Login uses JWT tokens for stateless API authentication.
- Passwords are stored with BCrypt hashing.
- Access control is enforced so users operate only on their own file/share data.
- Accounts must verify email before login is allowed.
- Register and forgot-password UI use a 10-second cooldown after sending email links.

### 3.1 Auth endpoints
- `POST /api/auth/register` creates a new account (or refreshes unverified account) and sends verification link.
- `GET /api/auth/verify?token=...` verifies account email.
- `POST /api/auth/resend-verification` resends verification email for unverified accounts.
- `POST /api/auth/forgot-password` sends a reset link (generic success response).
- `POST /api/auth/reset-password` sets a new password using reset token.

## Stack

- Java + Spring Boot
- React + Vite
- Gradle
- AWS S3
- H2 (local) / PostgreSQL (runtime option)

## Local setup

1. Run the backend:
```bash
./gradlew clean test
./gradlew bootRun
```

2. Run the frontend:
```bash
cd frontend
npm ci
npm run dev
```

3. If needed, set frontend API URL in `frontend/.env`:
```env
VITE_API_BASE_URL=http://localhost:8080
```

Default URLs:
- Backend: `http://localhost:8080`
- Frontend: `http://localhost:5173`

Frontend routes:
- `/verify-email?token=...`
- `/forgot-password`
- `/reset-password?token=...`

Note:
- Local deployment can share documents only with devices on the same Wi-Fi/LAN.
- `localhost` links are not accessible from external networks.

### Change `localhost` to LAN IP (for same Wi-Fi sharing)
1. Find your PC IPv4 address:
```bash
ipconfig
```
Use the active adapter IPv4 (example: `192.168.1.25`).

2. Set backend public base URL:
```bash
APP_PUBLIC_BASE_URL=http://192.168.1.25:8080
```
If you use `.properties` directly:
```properties
app.public-base-url=http://192.168.1.25:8080
```

3. Allow frontend origin in CORS:
```bash
APP_CORS_ALLOWED_ORIGINS=http://192.168.1.25:5173,http://localhost:5173
```

4. Restart backend and create a new share link.

5. Make sure firewall allows inbound TCP `8080` (and `5173` if frontend is accessed from other devices).

## Real Deployment
This section shows the real deployment configuration used to run CloudShare with production infrastructure (PostgreSQL, AWS S3, and strict CORS).
Use Spring profile `prod` to load `application-prod.yml` overrides.

### 1. Set active profile
```bash
SPRING_PROFILES_ACTIVE=prod
```

### 2. Set required environment variables
```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/<db>
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
SPRING_DATASOURCE_USERNAME=<db_user>
SPRING_DATASOURCE_PASSWORD=<db_password>

APP_JWT_SECRET=<long_random_secret>
APP_AWS_S3_BUCKET=<bucket_name>
APP_AWS_ACCESS_KEY=<aws_access_key>
APP_AWS_SECRET_KEY=<aws_secret_key>
APP_AWS_S3_PRESIGN_PUT_TTL_MINUTES=10

APP_PUBLIC_BASE_URL=https://<backend-domain>
APP_VERIFICATION_BASE_URL=https://<frontend-domain>
APP_VERIFICATION_TOKEN_TTL_MINUTES=30
APP_PASSWORD_RESET_BASE_URL=https://<frontend-domain>
APP_PASSWORD_RESET_TOKEN_TTL_MINUTES=30
APP_CORS_ALLOWED_ORIGINS=https://<frontend-domain>,https://www.<frontend-domain>

SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<smtp_username>
SPRING_MAIL_PASSWORD=<smtp_password_or_app_password>
SPRING_MAIL_SMTP_AUTH=true
SPRING_MAIL_SMTP_STARTTLS_ENABLE=true
```

### 3. Start backend in production mode
```bash
java -Dspring.profiles.active=prod -jar build/libs/FileShare-0.0.1-SNAPSHOT.jar
```

## S3 CORS requirement for direct upload
Because uploads are sent from browser directly to S3, bucket CORS must allow your frontend origin and `PUT`.

Example:
```json
[
  {
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedOrigins": ["http://localhost:5173", "http://127.0.0.1:5173"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3000
  }
]
```

## Project structure

```text
src/                  Spring Boot backend
frontend/             React frontend
```
