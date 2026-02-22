import { useEffect, useMemo, useState } from "react";
import DashboardLayout from "../layout/DashboardLayout";
import AlertBar from "../components/AlertBar";
import StatsCards from "../components/StatsCards";
import AuthPanel from "../components/AuthPanel";
import UploadPanel from "../components/UploadPanel";
import FilesTable from "../components/FilesTable";
import SharesTable from "../components/SharesTable";
import { apiClient } from "../util/apiClient";

function DashboardPage() {
  const [token, setSessionToken] = useState("");
  const [currentUserEmail, setCurrentUserEmail] = useState("");
  const [files, setFiles] = useState([]);
  const [shares, setShares] = useState([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [notice, setNotice] = useState({ kind: "info", message: "" });
  const [activeMenu, setActiveMenu] = useState("account");
  const [registerCooldownSeconds, setRegisterCooldownSeconds] = useState(0);

  const sessionActive = Boolean(token);
  const emailPattern = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
  const normalizeToken = (value) => (typeof value === "string" ? value.trim() : "");
  const normalizeEmail = (value) => String(value || "").trim().toLowerCase();

  const showNotice = (kind, message) => setNotice({ kind, message });

  const isValidEmail = (email) => emailPattern.test(String(email || "").trim());

  const normalizeAuthError = (error, mode) => {
    const raw = String(error?.message || "").trim().toLowerCase();
    if (!raw) return mode === "register" ? "Account existed." : "Password not correct.";

    if (mode === "register") {
      if (raw.includes("account existed") || raw.includes("already")) return "Account existed.";
      if (raw === "conflict") return "Account existed.";
    }

    if (mode === "login") {
      if (raw.includes("password")) return "Password not correct.";
      if (raw.includes("invalid credentials")) return "Password not correct.";
      if (raw === "not found" || raw.includes("account not found")) return "Account not found.";
    }

    return error.message;
  };

  const register = async (email, password) => {
    if (registerCooldownSeconds > 0) {
      showNotice("info", `Please wait ${registerCooldownSeconds}s before registering again.`);
      return;
    }

    if (!isValidEmail(email)) {
      showNotice("error", "Enter a valid email address.");
      return;
    }
    try {
      const response = await apiClient.register(email, password);
      showNotice("success", response.message || "Registration successful.");
      setRegisterCooldownSeconds(10);
    } catch (error) {
      showNotice("error", normalizeAuthError(error, "register"));
    }
  };

  const login = async (email, password) => {
    if (!isValidEmail(email)) {
      showNotice("error", "Enter a valid email address.");
      return;
    }
    try {
      const response = await apiClient.login(email, password);
      const nextToken = normalizeToken(response?.token);
      if (!nextToken) {
        showNotice("error", "Login response did not include a valid token.");
        return;
      }
      setSessionToken(nextToken);
      setCurrentUserEmail(normalizeEmail(email));
      setActiveMenu("files");
      showNotice("success", "Login successful.");
    } catch (error) {
      showNotice("error", normalizeAuthError(error, "login"));
    }
  };

  const logout = () => {
    setSessionToken("");
    setCurrentUserEmail("");
    setActiveMenu("account");
    setFiles([]);
    setShares([]);
    showNotice("info", "Logged out.");
  };

  const refreshFiles = async () => {
    if (!token) {
      setFiles([]);
      return;
    }
    try {
      const result = await apiClient.listFiles(token);
      setFiles(result || []);
    } catch (error) {
      showNotice("error", error.message);
    }
  };

  const refreshShares = async () => {
    if (!token) {
      setShares([]);
      return;
    }
    try {
      const result = await apiClient.listShares(token);
      setShares(result || []);
    } catch (error) {
      showNotice("error", error.message);
    }
  };

  const uploadFile = async (file) => {
    if (!token) {
      showNotice("error", "Please login first.");
      return;
    }
    try {
      await apiClient.uploadFile(token, file);
      showNotice("success", "File uploaded.");
      await refreshFiles();
    } catch (error) {
      showNotice("error", error.message);
    }
  };

  const deleteFile = async (key) => {
    if (!token) {
      showNotice("error", "Please login first.");
      return;
    }
    try {
      await apiClient.deleteFile(token, key);
      showNotice("success", "File deleted.");
      await Promise.all([refreshFiles(), refreshShares()]);
    } catch (error) {
      showNotice("error", error.message);
    }
  };

  const renameFile = async (id, nextName) => {
    if (!token) {
      showNotice("error", "Please login first.");
      return;
    }
    try {
      await apiClient.renameFile(token, id, nextName);
      showNotice("success", "File renamed.");
      await Promise.all([refreshFiles(), refreshShares()]);
    } catch (error) {
      showNotice("error", error.message);
    }
  };

  const createShare = async (key) => {
    if (!token) {
      showNotice("error", "Please login first.");
      return;
    }
    try {
      const result = await apiClient.createShare(token, key);
      showNotice("success", `Share created: ${result.publicDownloadUrl}`);
      await refreshShares();
    } catch (error) {
      showNotice("error", error.message);
    }
  };

  const revokeShare = async (id) => {
    if (!token) {
      showNotice("error", "Please login first.");
      return;
    }
    try {
      await apiClient.revokeShare(token, id);
      showNotice("success", "Share revoked.");
      await refreshShares();
    } catch (error) {
      showNotice("error", error.message);
    }
  };

  useEffect(() => {
    if (!token) {
      setFiles([]);
      setShares([]);
      setSearchQuery("");
      return;
    }
    refreshFiles();
    refreshShares();
  }, [token]);

  useEffect(() => {
    if (registerCooldownSeconds <= 0) return undefined;
    const timer = setInterval(() => {
      setRegisterCooldownSeconds((current) => (current <= 1 ? 0 : current - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [registerCooldownSeconds]);

  const normalizedSearch = searchQuery.trim().toLowerCase();
  const matchesSearch = (value) => String(value || "").toLowerCase().includes(normalizedSearch);

  const filteredFiles = useMemo(() => {
    if (!normalizedSearch) return files;
    return files.filter((file) =>
      matchesSearch(file.originalName) ||
      matchesSearch(file.s3Key) ||
      matchesSearch(file.fileName)
    );
  }, [files, normalizedSearch]);

  const filteredShares = useMemo(() => {
    if (!normalizedSearch) return shares;
    return shares.filter((share) =>
      matchesSearch(share.key) ||
      matchesSearch(share.publicDownloadUrl) ||
      matchesSearch(share.token)
    );
  }, [shares, normalizedSearch]);

  const recentFiles = filteredFiles.slice(0, 5);
  const initials = currentUserEmail
    ? currentUserEmail.split("@")[0].slice(0, 2).toUpperCase()
    : "GU";

  const menuItems = sessionActive
    ? [
        { key: "files", label: "My Files" },
        { key: "upload", label: "Upload" },
        { key: "shares", label: "Share Links" },
        { key: "logout", label: "Logout" }
      ]
    : [{ key: "account", label: "Account" }];

  const handleMenuSelect = (key) => {
    if (key === "logout") {
      const shouldLogout = window.confirm("Are you sure you want to logout?");
      if (shouldLogout) {
        logout();
      }
      return;
    }

    if (!sessionActive && key !== "account") {
      showNotice("error", "Please login first.");
      setActiveMenu("account");
      return;
    }
    setActiveMenu(key);
  };

  const renderMenuView = () => {
    switch (activeMenu) {
      case "files":
        return (
          <FilesTable
            sessionActive={sessionActive}
            token={token}
            files={filteredFiles}
            onRefresh={refreshFiles}
            onDelete={deleteFile}
            onRename={renameFile}
            onCreateShare={createShare}
            onStatus={showNotice}
          />
        );
      case "upload":
        return <UploadPanel disabled={!sessionActive} onUpload={uploadFile} />;
      case "shares":
        return (
          <SharesTable
            sessionActive={sessionActive}
            shares={filteredShares}
            onRefresh={refreshShares}
            onRevoke={revokeShare}
            onStatus={showNotice}
          />
        );
      case "stats":
        return (
          <section className="card">
            <h2>Statistics</h2>
            <StatsCards files={files} shares={shares} />
          </section>
        );
      case "account":
      default:
        return (
          <AuthPanel
            sessionActive={sessionActive}
            onRegister={register}
            onLogin={login}
            onLogout={logout}
            registerCooldownSeconds={registerCooldownSeconds}
          />
        );
    }
  };

  return (
    <DashboardLayout
      showSidebar
      menuItems={menuItems}
      activeMenu={activeMenu}
      onMenuSelect={handleMenuSelect}
      topRight={
        sessionActive ? (
          <div className="profileChip">
            <span className="avatar">{initials}</span>
            <span className="profileName">{currentUserEmail}</span>
          </div>
        ) : null
      }
    >
      {sessionActive && activeMenu !== "account" && (
        <section className="dashboardTopbar">
          <div className="searchBox">
            <input
              type="search"
              placeholder="Search files or share links..."
              disabled={!sessionActive}
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
            />
          </div>
        </section>
      )}

      <AlertBar kind={notice.kind} message={notice.message} />

      {sessionActive && <StatsCards files={files} shares={shares} />}

      {sessionActive && (
        <section className="recentStrip">
          <h2>Recent</h2>
          <div className="recentGrid">
            {recentFiles.length === 0 ? (
              <article className="recentCard muted">No recent files yet.</article>
            ) : (
              recentFiles.map((file) => (
                <article className="recentCard" key={file.id || file.s3Key}>
                  <strong>{file.originalName || "Untitled file"}</strong>
                  <span>{new Date(file.createdAt || Date.now()).toLocaleDateString()}</span>
                </article>
              ))
            )}
          </div>
        </section>
      )}

      <section className="workspace workspaceSingle">
        <div className="stack mainColumn">{renderMenuView()}</div>
      </section>
    </DashboardLayout>
  );
}

export default DashboardPage;
