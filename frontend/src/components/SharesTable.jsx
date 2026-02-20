function formatDate(value) {
  if (!value) return "No expiry";
  return new Date(value).toLocaleString();
}

function SharesTable({ sessionActive, shares, onRefresh, onRevoke, onStatus }) {
  const displayShares = sessionActive ? shares : [];

  const copyLink = async (url) => {
    try {
      await navigator.clipboard.writeText(url);
      onStatus("success", "Share link copied to clipboard.");
    } catch {
      onStatus("info", `Copy failed. Link: ${url}`);
    }
  };

  return (
    <section className="card">
      <div className="sectionHeader">
        <h2>Share Links</h2>
        <button className="ghost" onClick={onRefresh} disabled={!sessionActive}>
          Refresh
        </button>
      </div>
      <div className="tableWrap">
        <table>
          <thead>
            <tr>
              <th>File</th>
              <th>Expires</th>
              <th>Downloads</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {displayShares.length === 0 ? (
              <tr>
                <td colSpan={5}>
                  {sessionActive ? "No share links found." : "Login to view your share links."}
                </td>
              </tr>
            ) : (
              displayShares.map((share) => (
                <tr key={share.id}>
                  <td>{share.key?.split("/").pop()}</td>
                  <td>{formatDate(share.expiresAt)}</td>
                  <td>{share.downloadCount || 0}</td>
                  <td>{share.revoked ? "Revoked" : "Active"}</td>
                  <td className="rowActions">
                    <button onClick={() => copyLink(share.publicDownloadUrl)} disabled={!sessionActive}>
                      Copy Link
                    </button>
                    <button className="danger" onClick={() => onRevoke(share.id)} disabled={!sessionActive}>
                      Revoke
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}

export default SharesTable;
