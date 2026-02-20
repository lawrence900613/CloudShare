# CloudShare

CloudShare is a full-stack file storage app:
- Spring Boot backend
- React frontend
- AWS S3 for file storage

This project was built for self-learning and hands-on practice across a full-stack workflow, including API design, authentication, cloud file storage, sharing, testing, and frontend development.
The repo includes local development setup for both backend and frontend.

## What it does

- User registration and login
- Email verification flow
- Upload, list, download, and delete files
- Direct browser-to-S3 uploads using presigned URLs
- Real cloud rename (S3 copy + delete) with metadata/share-link rekey
- File previews in the UI (image, PDF, text-like files)
- Create and revoke share links

Sample UI
<img width="2265" height="1242" alt="image" src="https://github.com/user-attachments/assets/7e3402a4-c2fb-4019-9200-a0d2d39769b6" />


## Feature breakdown

### 1. File upload, download, and preview
- Files are uploaded directly from browser to S3 using presigned PUT URLs.
- Backend finalizes uploads by validating object ownership and saving metadata.
- Users can list only their own files and view basic metadata.
- Preview is supported in the dashboard for image, PDF, and text-like files.
- Download and delete actions are permission-scoped to file ownership.

### 1.1 Direct upload flow (presigned URL)
1. `POST /api/files/upload/presigned` returns `{ s3Key, uploadUrl, method }`.
2. Frontend uploads bytes directly to S3 using the returned URL.
3. `POST /api/files/upload/complete` persists metadata after S3 object verification.

Deprecated endpoint:
- `POST /api/files/upload` remains available but is deprecated.

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
- Email-based registration and verification are included in the auth flow.
- Access control is enforced so users operate only on their own file/share data.

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
npm install
npm run dev
```

3. If needed, set frontend API URL in `frontend/.env`:
```env
VITE_API_BASE_URL=http://localhost:8080
```

Default URLs:
- Backend: `http://localhost:8080`
- Frontend: `http://localhost:5173`

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
