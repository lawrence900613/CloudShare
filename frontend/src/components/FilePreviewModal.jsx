import { useEffect, useMemo, useState } from "react";
import { apiClient } from "../util/apiClient";

async function fetchS3File(token, key) {
  const response = await fetch(
    `${apiClient.baseUrl}/api/files/s3/download?key=${encodeURIComponent(key)}`,
    { headers: { Authorization: `Verify ${token}` } }
  );
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || "File request failed");
  }
  const blob = await response.blob();
  const disposition = response.headers.get("content-disposition") || "";
  const contentType = response.headers.get("content-type") || blob.type || "";
  const matched = /filename="?([^"]+)"?/i.exec(disposition);
  const fileName = matched?.[1] || key.split("/").pop() || "download.bin";
  return { blob, fileName, contentType };
}

function isTextPreview(contentType, fileName) {
  const lowerName = String(fileName || "").toLowerCase();
  return (
    contentType.startsWith("text/") ||
    contentType.includes("json") ||
    lowerName.endsWith(".txt") ||
    lowerName.endsWith(".json") ||
    lowerName.endsWith(".csv") ||
    lowerName.endsWith(".md")
  );
}

async function downloadS3File(token, key) {
  const { blob, fileName } = await fetchS3File(token, key);
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
}

function FilePreviewModal({ token, s3Key, onClose, onStatus }) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [preview, setPreview] = useState(null);

  const previewObjectUrl = useMemo(() => {
    if (!preview?.blob) return "";
    return URL.createObjectURL(preview.blob);
  }, [preview]);

  useEffect(() => {
    return () => { if (previewObjectUrl) URL.revokeObjectURL(previewObjectUrl); };
  }, [previewObjectUrl]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    setPreview(null);
    fetchS3File(token, s3Key)
      .then(async (result) => {
        if (cancelled) return;
        const normalizedType = String(result.contentType || "").split(";")[0].trim().toLowerCase();
        const textPreview = isTextPreview(normalizedType, result.fileName)
          ? await result.blob.text()
          : "";
        setPreview({ ...result, key: s3Key, contentType: normalizedType, textPreview });
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || "Preview failed");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [token, s3Key]);

  const handleOverlayClick = (e) => {
    if (e.target === e.currentTarget) onClose();
  };

  return (
    <div className="previewOverlay" role="dialog" aria-modal="true" aria-label="File preview" onClick={handleOverlayClick}>
      <div className="previewModal">
        <div className="sectionHeader">
          <h2>Preview</h2>
          <button className="ghost" onClick={onClose}>Close</button>
        </div>

        {loading && <p className="muted">Loading preview...</p>}
        {!loading && error && <div className="alert alert-error">{error}</div>}

        {!loading && preview && (
          <div className="previewBody">
            {preview.contentType.startsWith("image/") && (
              <img className="previewImage" src={previewObjectUrl} alt={preview.fileName} />
            )}
            {preview.contentType === "application/pdf" && (
              <iframe className="previewFrame" title={preview.fileName} src={previewObjectUrl} />
            )}
            {isTextPreview(preview.contentType, preview.fileName) && (
              <pre>{preview.textPreview}</pre>
            )}
            {!preview.contentType.startsWith("image/") &&
              preview.contentType !== "application/pdf" &&
              !isTextPreview(preview.contentType, preview.fileName) && (
                <p className="muted">
                  Preview not available for this file type ({preview.contentType || "unknown"}).
                </p>
              )}
            <div className="actions">
              <button
                onClick={async () => {
                  try {
                    await downloadS3File(token, preview.key);
                    onStatus?.("success", "Download completed.");
                  } catch (err) { onStatus?.("error", err.message); }
                }}
              >
                Download
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default FilePreviewModal;
