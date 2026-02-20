function DashboardLayout({ children }) {
  return (
    <div className="appShell">
      <header className="appHeader">
        <div>
          <h1>FileShare Web App</h1>
          <p>Upload, manage, and share files securely.</p>
        </div>
      </header>
      <main>{children}</main>
    </div>
  );
}

export default DashboardLayout;
