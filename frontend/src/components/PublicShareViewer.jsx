import { useState } from "react";
import { apiClient } from "../util/apiClient";

function PublicShareViewer({ onStatus }) {
  const [token, setToken] = useState("");
  const [payload, setPayload] = useState(null);

  const loadShare = async () => {
    if (!token.trim()) {
      onStatus("error", "Share token is required.");
      return;
    }
    try {
      const result = await apiClient.getPublicShare(token.trim());
      setPayload(result);
      onStatus("success", "Public share loaded.");
    } catch (error) {
      setPayload(null);
      onStatus("error", error.message);
    }
  };

  const downloadShare = async () => {
    if (!payload?.publicDownloadUrl) return;
    try {
      const response = await fetch(payload.publicDownloadUrl);
      if (!response.ok) {
        throw new Error("Public download failed");
      }
      const blob = await response.blob();
      const disposition = response.headers.get("content-disposition") || "";
      const matched = /filename="?([^"]+)"?/i.exec(disposition);
      const fileName = matched?.[1] || payload.fileName || "download.bin";
      const link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = fileName;
      document.body.appendChild(link);
      link.click();
      link.remove();
      onStatus("success", `Public download completed: ${fileName}`);
    } catch (error) {
      onStatus("error", error.message);
    }
  };

  return (
    <section className="card">
      <h2>Public Share Viewer</h2>
      <label>
        Share token
        <input
          value={token}
          onChange={(event) => setToken(event.target.value)}
          placeholder="Paste share token"
        />
      </label>
      <div className="actions">
        <button onClick={loadShare}>Load</button>
        <button className="ghost" onClick={downloadShare} disabled={!payload?.publicDownloadUrl}>
          Download
        </button>
      </div>
      {payload && (
        <div className="publicMeta">
          <div>
            <strong>Name:</strong> {payload.fileName}
          </div>
          <div>
            <strong>Type:</strong> {payload.mimeType || "application/octet-stream"}
          </div>
          <div>
            <strong>Downloads:</strong> {payload.downloadCount || 0}
          </div>
          <div>
            <strong>Expires:</strong>{" "}
            {payload.expiresAt ? new Date(payload.expiresAt).toLocaleString() : "No expiry"}
          </div>
        </div>
      )}
    </section>
  );
}

export default PublicShareViewer;
