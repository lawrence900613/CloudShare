import { UPLOAD_RATE_LIMIT_COUNT, UPLOAD_RATE_LIMIT_WINDOW_MINUTES } from "./frontendConfig";
const API_BASE = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, options);
  const text = await response.text();
  let payload = text;

  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    payload = text;
  }

  if (!response.ok) {
    const baseMessage =
      (payload && typeof payload === "object" && (payload.message || payload.error)) ||
      (typeof payload === "string" ? payload : "Request failed");
    const retryAfterHeader = response.headers.get("Retry-After");
    const retryAfterSeconds = retryAfterHeader ? Number.parseInt(retryAfterHeader, 10) : NaN;

    let message = baseMessage;
    if (response.status === 429) {
      if (path.startsWith("/api/files/upload")) {
        message = Number.isFinite(retryAfterSeconds) && retryAfterSeconds > 0
          ? `Upload limit reached. You can upload up to ${UPLOAD_RATE_LIMIT_COUNT} files per ${UPLOAD_RATE_LIMIT_WINDOW_MINUTES} minutes. Try again in ${retryAfterSeconds}s.`
          : `Upload limit reached. You can upload up to ${UPLOAD_RATE_LIMIT_COUNT} files per ${UPLOAD_RATE_LIMIT_WINDOW_MINUTES} minutes.`;
      } else {
        message = Number.isFinite(retryAfterSeconds) && retryAfterSeconds > 0
          ? `Too many requests. Please try again in ${retryAfterSeconds}s.`
          : "Too many requests. Please try again later.";
      }
    }

    const error = new Error(message);
    error.status = response.status;
    if (Number.isFinite(retryAfterSeconds) && retryAfterSeconds > 0) {
      error.retryAfterSeconds = retryAfterSeconds;
    }
    throw error;
  }

  return payload;
}

export const apiClient = {
  baseUrl: API_BASE,

  register(email, password) {
    return request("/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password })
    });
  },

  login(email, password) {
    return request("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password })
    });
  },

  verifyEmail(token) {
    return request(`/api/auth/verify?token=${encodeURIComponent(token)}`);
  },

  resendVerification(email) {
    return request("/api/auth/resend-verification", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email })
    });
  },

  forgotPassword(email) {
    return request("/api/auth/forgot-password", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email })
    });
  },

  resetPassword(token, password) {
    return request("/api/auth/reset-password", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token, password })
    });
  },

  listFiles(token) {
    return request("/api/files?page=0&size=200", {
      headers: { Authorization: `Verify ${token}` }
    });
  },

  uploadFile(token, file) {
    const mimeType = file.type || "application/octet-stream";

    return request("/api/files/upload/presigned", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Verify ${token}`
      },
      body: JSON.stringify({
        originalName: file.name,
        mimeType,
        sizeBytes: file.size
      })
    }).then(async (presigned) => {
      const uploadResponse = await fetch(presigned.uploadUrl, {
        method: presigned.method || "PUT",
        headers: { "Content-Type": mimeType },
        body: file
      });

      if (!uploadResponse.ok) {
        throw new Error("Direct upload to S3 failed");
      }

      return request("/api/files/upload/complete", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Verify ${token}`
        },
        body: JSON.stringify({
          s3Key: presigned.s3Key,
          originalName: file.name,
          mimeType,
          sizeBytes: file.size
        })
      });
    });
  },

  deleteFile(token, key) {
    return request(`/api/files/s3?key=${encodeURIComponent(key)}`, {
      method: "DELETE",
      headers: { Authorization: `Verify ${token}` }
    });
  },

  renameFile(token, id, originalName) {
    return request(`/api/files/${encodeURIComponent(id)}`, {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Verify ${token}`
      },
      body: JSON.stringify({ originalName })
    });
  },

  createShare(token, key) {
    return request("/api/shares", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Verify ${token}`
      },
      body: JSON.stringify({ key, expiresInMinutes: 1440 })
    });
  },

  listShares(token) {
    return request("/api/shares", {
      headers: { Authorization: `Verify ${token}` }
    });
  },

  revokeShare(token, id) {
    return request(`/api/shares/${id}`, {
      method: "DELETE",
      headers: { Authorization: `Verify ${token}` }
    });
  },

  getPublicShare(token) {
    return request(`/api/shares/public/${encodeURIComponent(token)}`);
  }
};
