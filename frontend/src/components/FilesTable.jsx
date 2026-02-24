import { useMemo, useState } from "react";
import { apiClient } from "../util/apiClient";

function formatBytes(value) {
  const size = Number(value || 0);
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDate(value) {
  if (!value) return "-";
  return new Date(value).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

function getFileType(mimeType, name) {
  const mime = String(mimeType || "").toLowerCase();
  const ext = String(name || "").split(".").pop().toLowerCase();
  if (mime.includes("pdf") || ext === "pdf")
    return { label: "PDF", typeName: "PDF", cls: "fileTypeIcon-pdf" };
  if (mime.includes("word") || mime.includes("document") || ["doc", "docx"].includes(ext))
    return { label: "DOC", typeName: "Document", cls: "fileTypeIcon-doc" };
  if (mime.includes("sheet") || mime.includes("excel") || ["xls", "xlsx", "csv"].includes(ext))
    return { label: "XLS", typeName: "Spreadsheet", cls: "fileTypeIcon-xls" };
  if (mime.startsWith("image/") || ["png", "jpg", "jpeg", "gif", "webp", "svg"].includes(ext))
    return { label: "IMG", typeName: "Image", cls: "fileTypeIcon-img" };
  if (mime.startsWith("video/") || ["mp4", "mov", "avi", "mkv"].includes(ext))
    return { label: "VID", typeName: "Video", cls: "fileTypeIcon-vid" };
  if (mime.includes("zip") || ["zip", "tar", "gz", "rar"].includes(ext))
    return { label: "ZIP", typeName: "Archive", cls: "fileTypeIcon-zip" };
  if (mime.startsWith("text/") || ["txt", "md"].includes(ext))
    return { label: "TXT", typeName: "Text", cls: "fileTypeIcon-other" };
  const extLabel = ext.toUpperCase().slice(0, 3);
  return { label: extLabel || "FILE", typeName: "File", cls: "fileTypeIcon-other" };
}

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
  const matched = /filename="?([^"]+)"?/i.exec(disposition);
  const fileName = matched?.[1] || key.split("/").pop() || "download.bin";
  return { blob, fileName };
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

function resolveKey(file) { return file?.s3Key || file?.key || ""; }
function resolveName(file) { return file?.originalName || file?.fileName || resolveKey(file); }
function resolveSize(file) { return file?.sizeBytes || 0; }
function resolveDate(file) { return file?.createdAt || file?.lastModified || ""; }

const TYPE_OPTIONS = [
  { value: "all", label: "All types" },
  { value: "Image", label: "Image" },
  { value: "Document", label: "Document" },
  { value: "PDF", label: "PDF" },
  { value: "Spreadsheet", label: "Spreadsheet" },
  { value: "Video", label: "Video" },
  { value: "Archive", label: "Archive" },
  { value: "Text", label: "Text" },
  { value: "File", label: "Other" },
];

const IconChevron = () => (
  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="6 9 12 15 18 9" />
  </svg>
);

function SortIndicator({ column, sortKey, sortDir }) {
  if (sortKey !== column) {
    return <span className="sortIndicator sortIndicator-idle">↕</span>;
  }
  return (
    <span className="sortIndicator sortIndicator-active">
      {sortDir === "asc" ? "↑" : "↓"}
    </span>
  );
}

function FilesTable({ sessionActive, token, files, onRefresh, onDelete, onRename, onCreateShare, onStatus, onPreview }) {
  const [filterType, setFilterType] = useState("all");
  const [sortKey, setSortKey] = useState("date");
  const [sortDir, setSortDir] = useState("desc");

  const allFiles = sessionActive ? files : [];

  const processedFiles = useMemo(() => {
    let result = [...allFiles];

    if (filterType !== "all") {
      result = result.filter((f) => getFileType(f.mimeType, resolveName(f)).typeName === filterType);
    }

    result.sort((a, b) => {
      let aVal, bVal;
      if (sortKey === "name") {
        aVal = resolveName(a).toLowerCase();
        bVal = resolveName(b).toLowerCase();
      } else if (sortKey === "type") {
        aVal = getFileType(a.mimeType, resolveName(a)).typeName;
        bVal = getFileType(b.mimeType, resolveName(b)).typeName;
      } else if (sortKey === "size") {
        aVal = resolveSize(a);
        bVal = resolveSize(b);
      } else {
        aVal = resolveDate(a) || "";
        bVal = resolveDate(b) || "";
      }
      if (aVal < bVal) return sortDir === "asc" ? -1 : 1;
      if (aVal > bVal) return sortDir === "asc" ? 1 : -1;
      return 0;
    });

    return result;
  }, [allFiles, filterType, sortKey, sortDir]);

  const handleSort = (key) => {
    if (sortKey === key) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir("asc");
    }
  };

  const hasActiveFilter = filterType !== "all";

  return (
    <section className="card">
      <div className="sectionHeader">
        <h2>All Files</h2>
        <button className="ghost" onClick={onRefresh} disabled={!sessionActive}>
          Refresh
        </button>
      </div>

      {/* Filter toolbar */}
      <div className="tableFilterBar">
        <div className="tableFilterSelect">
          <select
            value={filterType}
            onChange={(e) => setFilterType(e.target.value)}
            disabled={!sessionActive}
          >
            {TYPE_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
          <span className="tableFilterSelectArrow"><IconChevron /></span>
        </div>
        {hasActiveFilter && (
          <button
            className="tableFilterClear"
            type="button"
            onClick={() => { setFilterType("all"); }}
          >
            Clear
          </button>
        )}
        <span className="tableFilterCount">
          {processedFiles.length} of {allFiles.length} file{allFiles.length !== 1 ? "s" : ""}
        </span>
      </div>

      <div className="tableWrap">
        <table>
          <thead>
            <tr>
              <th>
                <button className="sortTh" type="button" onClick={() => handleSort("name")}>
                  Name <SortIndicator column="name" sortKey={sortKey} sortDir={sortDir} />
                </button>
              </th>
              <th>
                <button className="sortTh" type="button" onClick={() => handleSort("type")}>
                  Type <SortIndicator column="type" sortKey={sortKey} sortDir={sortDir} />
                </button>
              </th>
              <th>
                <button className="sortTh" type="button" onClick={() => handleSort("size")}>
                  File size <SortIndicator column="size" sortKey={sortKey} sortDir={sortDir} />
                </button>
              </th>
              <th>
                <button className="sortTh" type="button" onClick={() => handleSort("date")}>
                  Last modified <SortIndicator column="date" sortKey={sortKey} sortDir={sortDir} />
                </button>
              </th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {processedFiles.length === 0 ? (
              <tr>
                <td colSpan={5}>
                  {!sessionActive
                    ? "Login to view your files."
                    : hasActiveFilter
                    ? "No files match your filter."
                    : "No files found."}
                </td>
              </tr>
            ) : (
              processedFiles.map((file) => {
                const key = resolveKey(file);
                const name = resolveName(file);
                const size = resolveSize(file);
                const date = resolveDate(file);
                const { label, typeName, cls } = getFileType(file.mimeType, name);

                return (
                  <tr key={key}>
                    <td>
                      <div className="fileNameCell">
                        <div className={`fileTypeIcon fileTypeIcon-sm ${cls}`}>{label}</div>
                        <span className="fileNameText">{name}</span>
                      </div>
                    </td>
                    <td><span className="typeBadge">{typeName}</span></td>
                    <td>{formatBytes(size)}</td>
                    <td>{formatDate(date)}</td>
                    <td className="rowActions">
                      <button onClick={() => onPreview?.(key)} disabled={!sessionActive}>
                        Preview
                      </button>
                      <button onClick={() => onCreateShare(key)} disabled={!sessionActive}>
                        Share
                      </button>
                      <button
                        disabled={!sessionActive || !file.id}
                        onClick={async () => {
                          const nextName = window.prompt("Enter new file name", name);
                          if (!nextName?.trim()) return;
                          try { await onRename(file.id, nextName.trim()); } catch { /* parent shows error */ }
                        }}
                      >
                        Rename
                      </button>
                      <button
                        className="ghost"
                        disabled={!sessionActive}
                        onClick={async () => {
                          try {
                            await downloadS3File(token, key);
                            onStatus("success", "Download completed.");
                          } catch (error) { onStatus("error", error.message); }
                        }}
                      >
                        Download
                      </button>
                      <button className="danger" onClick={() => onDelete(key)} disabled={!sessionActive}>
                        Delete
                      </button>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}

export default FilesTable;
