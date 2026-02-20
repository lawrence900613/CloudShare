function DashboardLayout({
  children,
  showSidebar = false,
  menuItems = [],
  activeMenu = "",
  onMenuSelect,
  topRight
}) {
  return (
    <div className={`layoutFrame ${showSidebar ? "" : "layoutFrameNoSidebar"}`}>
      {showSidebar && (
        <aside className="navRail">
          <div className="brandMark">
            <span className="brandDot" />
            <strong>FileShare</strong>
          </div>
          <nav className="sideMenu">
            {menuItems.map((item) => (
              <button
                key={item.key}
                className={`sideItem ${activeMenu === item.key ? "active" : ""}`}
                type="button"
                onClick={() => onMenuSelect && onMenuSelect(item.key)}
              >
                <span>{item.label}</span>
              </button>
            ))}
          </nav>
        </aside>
      )}
      <div className="appShell">
        <header className="appHeader">
          <div>
            <h1>FileShare Web App</h1>
            <p>Upload, manage, and share files securely.</p>
          </div>
          {topRight}
        </header>
        <main>{children}</main>
      </div>
    </div>
  );
}

export default DashboardLayout;
