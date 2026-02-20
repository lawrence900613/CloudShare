import { useEffect, useMemo, useState } from "react";
import { apiClient } from "../util/apiClient";

function formatBytes(value) {
  const size = Number(value || 0);
  if (size < 1024) return `${size} B`;
  return `${(size / 1024).toFixed(2)} KB`;
}

function formatDate(value) {
  if (!value) return "-";
  return new Date(value).toLocaleString();
}

async function fetchS3File(token, key) {
  const response = await fetch(
    `${apiClient.baseUrl}/api/files/s3/download?key=${encodeURIComponent(key)}`,
    {
      headers: { Authorization: `Verify ${token}` }
    }
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

async function downloadS3File(token, key) {
  const { blob, fileName } = await fetchS3File(token, key);
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
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

function FilesTable({ sessionActive, token, files, onRefresh, onDelete, onCreateShare, onStatus }) {
  const displayFiles = sessionActive ? files : [];
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState("");
  const [preview, setPreview] = useState(null);

  const previewObjectUrl = useMemo(() => {
    if (!preview?.blob) return "";
    return URL.createObjectURL(preview.blob);
  }, [preview]);

  useEffect(() => {
    return () => {
      if (previewObjectUrl) URL.revokeObjectURL(previewObjectUrl);
    };
  }, [previewObjectUrl]);

  useEffect(() => {
    if (!sessionActive) {
      setPreviewOpen(false);
      setPreview(null);
      setPreviewError("");
      setPreviewLoading(false);
    }
  }, [sessionActive]);

  const closePreview = () => {
    setPreviewOpen(false);
    setPreview(null);
    setPreviewError("");
    setPreviewLoading(false);
  };

  const openPreview = async (key) => {
    if (!sessionActive) return;
    setPreviewOpen(true);
    setPreviewLoading(true);
    setPreviewError("");
    setPreview(null);
    try {
      const result = await fetchS3File(token, key);
      const normalizedType = String(result.contentType || "").split(";")[0].trim().toLowerCase();
      const textPreview = isTextPreview(normalizedType, result.fileName)
        ? await result.blob.text()
        : "";
      setPreview({ ...result, key, contentType: normalizedType, textPreview });
    } catch (error) {
      setPreviewError(error.message || "Preview failed");
    } finally {
      setPreviewLoading(false);
    }
  };

  return (
    <>
      <section className="card">
        <div className="sectionHeader">
          <h2>My Files</h2>
          <button className="ghost" onClick={onRefresh} disabled={!sessionActive}>
            Refresh
          </button>
        </div>
        <div className="tableWrap">
          <table>
            <thead>
              <tr>
                <th>File Name</th>
                <th>Size</th>
                <th>Last Modified</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {displayFiles.length === 0 ? (
                <tr>
                  <td colSpan={4}>
                    {sessionActive ? "No files found." : "Login to view your files."}
                  </td>
                </tr>
              ) : (
                displayFiles.map((file) => (
                  <tr key={file.key}>
                    <td>{file.fileName || file.key}</td>
                    <td>{formatBytes(file.sizeBytes)}</td>
                    <td>{formatDate(file.lastModified)}</td>
                    <td className="rowActions">
                      <button onClick={() => openPreview(file.key)} disabled={!sessionActive}>
                        Preview
                      </button>
                      <button onClick={() => onCreateShare(file.key)} disabled={!sessionActive}>
                        Share
                      </button>
                      <button
                        className="ghost"
                        disabled={!sessionActive}
                        onClick={async () => {
                          try {
                            await downloadS3File(token, file.key);
                            onStatus("success", "Download completed.");
                          } catch (error) {
                            onStatus("error", error.message);
                          }
                        }}
                      >
                        Download
                      </button>
                      <button className="danger" onClick={() => onDelete(file.key)} disabled={!sessionActive}>
                        Delete
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {previewOpen && (
        <div className="previewOverlay" role="dialog" aria-modal="true" aria-label="File preview">
          <div className="previewModal">
            <div className="sectionHeader">
              <h2>Preview</h2>
              <button className="ghost" onClick={closePreview}>
                Close
              </button>
            </div>

            {previewLoading && <p className="muted">Loading preview...</p>}

            {!previewLoading && previewError && <div className="alert alert-error">{previewError}</div>}

            {!previewLoading && preview && (
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
                      Preview is not available for this file type ({preview.contentType || "unknown"}).
                    </p>
                  )}

                <div className="actions">
                  <button
                    onClick={async () => {
                      try {
                        await downloadS3File(token, preview.key);
                        onStatus("success", "Download completed.");
                      } catch (error) {
                        onStatus("error", error.message);
                      }
                    }}
                  >
                    Download
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </>
  );
}

export default FilesTable;
