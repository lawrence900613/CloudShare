# CloudShare

CloudShare is a full-stack file storage app:
- Spring Boot backend
- React frontend
- AWS S3 for file storage

This project is built for self-learning and hands-on practice with a full backend workflow: designing APIs, authentication, file storage, sharing, and testing.

The repo includes local development setup for both backend and frontend.

## What it does

- User registration and login
- Email verification flow
- Upload, list, download, and delete files
- File previews in the UI (image, PDF, text-like files)
- Create and revoke share links

## Feature breakdown

### 1. File upload, download, and preview
- Files are uploaded to S3 through authenticated API endpoints.
- Users can list only their own files and view basic metadata.
- Preview is supported in the dashboard for image, PDF, and text-like files.
- Download and delete actions are permission-scoped to file ownership.

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

## Project structure

```text
src/                  Spring Boot backend
frontend/             React frontend
```
