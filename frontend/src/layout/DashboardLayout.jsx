function DashboardLayout({
  children,
  showSidebar = false,
  menuItems = [],
  activeMenu = "",
  onMenuSelect,
  topLeft,
  topRight,
  sideBottom
}) {
  return (
    <div className={`layoutFrame ${showSidebar ? "" : "layoutFrameNoSidebar"}`}>
      {showSidebar && (
        <aside className="navRail">
          <div className="brandMark">
            <span className="brandDot">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                <polyline points="14,2 14,8 20,8" />
              </svg>
            </span>
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
                {item.icon && <span className="sideIcon">{item.icon}</span>}
                <span>{item.label}</span>
              </button>
            ))}
          </nav>
          {sideBottom && <div className="sideBottom">{sideBottom}</div>}
        </aside>
      )}
      <div className="appShell">
        <header className="appHeader">
          {topLeft && <div className="appHeaderLeft">{topLeft}</div>}
          {topRight && <div className="appHeaderRight">{topRight}</div>}
        </header>
        <main>{children}</main>
      </div>
    </div>
  );
}

export default DashboardLayout;
