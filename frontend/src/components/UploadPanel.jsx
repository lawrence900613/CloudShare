import { useState } from "react";

function UploadPanel({ disabled, onUpload }) {
  const [file, setFile] = useState(null);

  async function handleUpload() {
    if (!file) return;
    await onUpload(file);
    setFile(null);
    const input = document.getElementById("upload-input");
    if (input) input.value = "";
  }

  return (
    <section className="card">
      <h2>Upload</h2>
      <label>
        Choose file
        <input
          id="upload-input"
          type="file"
          onChange={(event) => setFile(event.target.files?.[0] || null)}
          disabled={disabled}
        />
      </label>
      <div className="actions">
        <button onClick={handleUpload} disabled={disabled || !file}>
          Upload
        </button>
      </div>
    </section>
  );
}

export default UploadPanel;
