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

function StatsCards({ files, shares }) {
  const totalFiles = files.length;
  const totalStorage = files.reduce((sum, file) => sum + Number(file.sizeBytes || 0), 0);
  const activeShares = shares.filter((share) => !share.revoked).length;

  return (
    <section className="statsGrid">
      <article className="statCard">
        <span>Total Files</span>
        <strong>{totalFiles}</strong>
      </article>
      <article className="statCard">
        <span>Total Storage</span>
        <strong>{formatBytes(totalStorage)}</strong>
      </article>
      <article className="statCard">
        <span>Active Shares</span>
        <strong>{activeShares}</strong>
      </article>
    </section>
  );
}

export default StatsCards;
