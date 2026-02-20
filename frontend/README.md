# FileShare React Frontend

## Setup

1. Copy `.env.example` to `.env`.
2. Update `VITE_API_BASE_URL` if your Spring backend is not on `http://localhost:8080`.
3. Install dependencies:

```bash
npm install
```

4. Start frontend (Vite):

```bash
npm run dev
```

Frontend runs on `http://localhost:5173` by default.

## Features

- Register and login
- Email verification flow
- Upload files
- List files for current user
- Download file
- Delete file
- Create share links
- List and revoke share links
- Public share viewer by token
