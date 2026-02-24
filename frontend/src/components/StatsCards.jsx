function formatBytes(value) {
  const size = Number(value || 0);
  if (size < 1024) return `${size} B`;
  const units = ["KB", "MB", "GB", "TB"];
  let current = size / 1024;
  let idx = 0;
  while (current >= 1024 && idx < units.length - 1) {
    current /= 1024;
    idx += 1;
  }
  return `${current.toFixed(2)} ${units[idx]}`;
}

const FilesIcon = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
    <polyline points="14,2 14,8 20,8" />
    <line x1="16" y1="13" x2="8" y2="13" />
    <line x1="16" y1="17" x2="8" y2="17" />
    <polyline points="10,9 9,9 8,9" />
  </svg>
);

const StorageIcon = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <ellipse cx="12" cy="5" rx="9" ry="3" />
    <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3" />
    <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5" />
  </svg>
);

const ShareIcon = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="18" cy="5" r="3" />
    <circle cx="6" cy="12" r="3" />
    <circle cx="18" cy="19" r="3" />
    <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
    <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
  </svg>
);

function StatsCards({ files, shares }) {
  const totalFiles = files.length;
  const totalStorage = files.reduce((sum, file) => sum + Number(file.sizeBytes || 0), 0);
  const activeShares = shares.filter((share) => !share.revoked).length;

  return (
    <section className="statsGrid">
      <article className="statCard">
        <div className="statCard-icon" style={{ background: "#eff6ff", color: "#3b82f6" }}>
          <FilesIcon />
        </div>
        <div className="statCard-body">
          <span>Total Files</span>
          <strong>{totalFiles}</strong>
        </div>
      </article>
      <article className="statCard">
        <div className="statCard-icon" style={{ background: "#f0fdfa", color: "#0d9488" }}>
          <StorageIcon />
        </div>
        <div className="statCard-body">
          <span>Total Storage</span>
          <strong>{formatBytes(totalStorage)}</strong>
        </div>
      </article>
      <article className="statCard">
        <div className="statCard-icon" style={{ background: "#faf5ff", color: "#a855f7" }}>
          <ShareIcon />
        </div>
        <div className="statCard-body">
          <span>Active Shares</span>
          <strong>{activeShares}</strong>
        </div>
      </article>
    </section>
  );
}

export default StatsCards;
