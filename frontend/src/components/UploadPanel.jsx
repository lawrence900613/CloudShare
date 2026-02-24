import { useState } from "react";
import { MAX_FILE_SIZE_MB } from "../util/frontendConfig";

const MAX_FILE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024;

function UploadPanel({ disabled, onUpload, maxFilesPerAccount = 3, currentFileCount = 0 }) {
  const [file, setFile] = useState(null);
  const [sizeError, setSizeError] = useState("");

  const atLimit = currentFileCount >= maxFilesPerAccount;

  function handleFileChange(event) {
    const selected = event.target.files?.[0] || null;
    if (selected && selected.size > MAX_FILE_BYTES) {
      setSizeError(`File too large. Maximum allowed size is ${MAX_FILE_SIZE_MB} MB (selected: ${(selected.size / (1024 * 1024)).toFixed(1)} MB).`);
      setFile(null);
      event.target.value = "";
    } else {
      setSizeError("");
      setFile(selected);
    }
  }

  async function handleUpload() {
    if (!file || atLimit) return;
    await onUpload(file);
    setFile(null);
    setSizeError("");
    const input = document.getElementById("upload-input");
    if (input) input.value = "";
  }

  return (
    <section className="card">
      <h2>Upload</h2>
      <p className="muted">
        {currentFileCount} / {maxFilesPerAccount} files used &middot; Max file size: {MAX_FILE_SIZE_MB} MB per file
      </p>

      {atLimit ? (
        <div className="alert alert-error">
          File limit reached ({maxFilesPerAccount}/{maxFilesPerAccount}). Delete a file to upload a new one.
        </div>
      ) : (
        <>
          {sizeError && <div className="alert alert-error">{sizeError}</div>}
          <label>
            Choose file
            <input
              id="upload-input"
              type="file"
              onChange={handleFileChange}
              disabled={disabled}
            />
          </label>
          <div className="actions">
            <button onClick={handleUpload} disabled={disabled || !file}>
              Upload
            </button>
          </div>
        </>
      )}
    </section>
  );
}

export default UploadPanel;
