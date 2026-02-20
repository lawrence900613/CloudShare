function AlertBar({ kind = "info", message }) {
  if (!message) return null;
  return <div className={`alert alert-${kind}`}>{message}</div>;
}

export default AlertBar;
