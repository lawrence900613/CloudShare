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
    const message =
      (payload && typeof payload === "object" && (payload.message || payload.error)) ||
      (typeof payload === "string" ? payload : "Request failed");
    throw new Error(message);
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

  listFiles(token) {
    return request("/api/files/s3", {
      headers: { Authorization: `Verify ${token}` }
    });
  },

  uploadFile(token, file) {
    const body = new FormData();
    body.append("file", file);
    return request("/api/files/upload", {
      method: "POST",
      headers: { Authorization: `Verify ${token}` },
      body
    });
  },

  deleteFile(token, key) {
    return request(`/api/files/s3?key=${encodeURIComponent(key)}`, {
      method: "DELETE",
      headers: { Authorization: `Verify ${token}` }
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
