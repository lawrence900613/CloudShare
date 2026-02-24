import { useEffect, useMemo, useState } from "react";
import DashboardLayout from "../layout/DashboardLayout";
import AlertBar from "../components/AlertBar";
import StatsCards from "../components/StatsCards";
import AuthPanel from "../components/AuthPanel";
import UploadPanel from "../components/UploadPanel";
import FilesTable from "../components/FilesTable";
import SharesTable from "../components/SharesTable";
import FilePreviewModal from "../components/FilePreviewModal";
import { apiClient } from "../util/apiClient";
import {
  MAX_ACTIVE_SHARE_LINKS,
  MAX_FILE_SIZE_MB,
  MAX_FILES_PER_ACCOUNT,
  STORAGE_CAPACITY_MB,
  UPLOAD_RATE_LIMIT_COUNT,
  UPLOAD_RATE_LIMIT_WINDOW_MINUTES
} from "../util/frontendConfig";
const SESSION_AUTH_STORAGE_KEY = "fileshare.session.auth";
const PERSISTENT_AUTH_STORAGE_KEY = "fileshare.persistent.auth";
const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;

function readJsonStorage(storage, key) {
  try {
    const raw = storage.getItem(key);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function writeJsonStorage(storage, key, value) {
  try {
    storage.setItem(key, JSON.stringify(value));
  } catch {
    // Ignore storage write failures.
  }
}

function clearAuthStorage() {
  try { window.localStorage.removeItem(PERSISTENT_AUTH_STORAGE_KEY); } catch {}
  try { window.sessionStorage.removeItem(SESSION_AUTH_STORAGE_KEY); } catch {}
}

function loadStoredAuth() {
  if (typeof window === "undefined") return { token: "", email: "" };

  const persistent = readJsonStorage(window.localStorage, PERSISTENT_AUTH_STORAGE_KEY);
  if (persistent?.token && persistent?.email && Number.isFinite(persistent.expiresAt)) {
    if (persistent.expiresAt > Date.now()) {
      return { token: String(persistent.token), email: String(persistent.email) };
    }
    try { window.localStorage.removeItem(PERSISTENT_AUTH_STORAGE_KEY); } catch {}
  }

  const session = readJsonStorage(window.sessionStorage, SESSION_AUTH_STORAGE_KEY);
  if (session?.token && session?.email) {
    return { token: String(session.token), email: String(session.email) };
  }

  return { token: "", email: "" };
}

/* ── File type helper ─────────────────────────────────────────────────────── */
function getFileType(mimeType, name) {
  const mime = String(mimeType || "").toLowerCase();
  const ext = String(name || "").split(".").pop().toLowerCase();
  if (mime.includes("pdf") || ext === "pdf")
    return { label: "PDF", cls: "fileTypeIcon-pdf" };
  if (mime.includes("word") || mime.includes("document") || ["doc", "docx"].includes(ext))
    return { label: "DOC", cls: "fileTypeIcon-doc" };
  if (mime.includes("sheet") || mime.includes("excel") || ["xls", "xlsx", "csv"].includes(ext))
    return { label: "XLS", cls: "fileTypeIcon-xls" };
  if (mime.startsWith("image/") || ["png", "jpg", "jpeg", "gif", "webp", "svg"].includes(ext))
    return { label: "IMG", cls: "fileTypeIcon-img" };
  if (mime.startsWith("video/") || ["mp4", "mov", "avi", "mkv"].includes(ext))
    return { label: "VID", cls: "fileTypeIcon-vid" };
  if (mime.includes("zip") || ["zip", "tar", "gz", "rar"].includes(ext))
    return { label: "ZIP", cls: "fileTypeIcon-zip" };
  const extLabel = ext.toUpperCase().slice(0, 3);
  return { label: extLabel || "FILE", cls: "fileTypeIcon-other" };
}

/* ── Storage widget ───────────────────────────────────────────────────────── */
function StorageWidget({ usedBytes, totalMB }) {
  const usedMB = usedBytes / (1024 ** 2);
  const pct = Math.min(100, (usedMB / totalMB) * 100);
  const usedLabel =
    usedMB < 0.01
      ? `${(usedBytes / 1024).toFixed(0)} KB`
      : `${usedMB.toFixed(1)} MB`;
  return (
    <div className="storageWidget">
      <p className="storageLabel">Storage</p>
      <div className="storageBar">
        <div className="storageBarFill" style={{ width: `${pct}%` }} />
      </div>
      <p className="storageUsed">{usedLabel} of {totalMB} MB</p>
      <button className="upgradeBtn" type="button">Upgrade now</button>
    </div>
  );
}

/* ── Sidebar icons ────────────────────────────────────────────────────────── */
const IconFiles = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
    <polyline points="14,2 14,8 20,8" />
  </svg>
);

const IconUpload = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="16,16 12,12 8,16" />
    <line x1="12" y1="12" x2="12" y2="21" />
    <path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3" />
  </svg>
);

const IconShare = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="18" cy="5" r="3" />
    <circle cx="6" cy="12" r="3" />
    <circle cx="18" cy="19" r="3" />
    <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
    <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
  </svg>
);

const IconLogout = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
    <polyline points="16,17 21,12 16,7" />
    <line x1="21" y1="12" x2="9" y2="12" />
  </svg>
);

const IconAccount = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
    <circle cx="12" cy="7" r="4" />
  </svg>
);

const IconUploadSmall = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="16,16 12,12 8,16" />
    <line x1="12" y1="12" x2="12" y2="21" />
    <path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3" />
  </svg>
);

/* ── Profile dropdown ─────────────────────────────────────────────────────── */
function ProfileDropdown({ email, initials, onLogout, onSwitchAccount }) {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) return undefined;
    const handler = (e) => {
      if (!e.target.closest(".profileDropdownWrap")) setOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [open]);

  return (
    <div className="profileDropdownWrap">
      <button className="profileChip" type="button" onClick={() => setOpen((v) => !v)}>
        <span className="avatar">{initials}</span>
        <span className="profileName">{email}</span>
      </button>
      {open && (
        <div className="profileDropdown">
          <p className="profileDropdownEmail">{email}</p>
          <div className="profileDropdownDivider" />
          <button
            className="profileDropdownItem"
            type="button"
            onClick={() => { setOpen(false); onSwitchAccount(); }}
          >
            <IconAccount /> Switch account
          </button>
          <button
            className="profileDropdownItem profileDropdownItem-danger"
            type="button"
            onClick={() => { setOpen(false); onLogout(); }}
          >
            <IconLogout /> Logout
          </button>
        </div>
      )}
    </div>
  );
}

/* ── Page title labels ────────────────────────────────────────────────────── */
const pageTitles = {
  files: "My Storage",
  upload: "Upload File",
  shares: "Share Links",
  account: "Account",
  stats: "Statistics",
};

function DashboardPage() {
  const initialAuth = useMemo(() => loadStoredAuth(), []);
  const [token, setSessionToken] = useState(initialAuth.token);
  const [currentUserEmail, setCurrentUserEmail] = useState(initialAuth.email);
  const [files, setFiles] = useState([]);
  const [shares, setShares] = useState([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [notice, setNotice] = useState({ kind: "info", message: "" });
  const [activeMenu, setActiveMenu] = useState(initialAuth.token ? "files" : "account");
  const [registerCooldownSeconds, setRegisterCooldownSeconds] = useState(0);
  const [previewS3Key, setPreviewS3Key] = useState(null);

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
    if (!isValidEmail(email)) { showNotice("error", "Enter a valid email address."); return; }
    try {
      const response = await apiClient.register(email, password);
      showNotice("success", response.message || "Registration successful.");
      setRegisterCooldownSeconds(10);
    } catch (error) {
      showNotice("error", normalizeAuthError(error, "register"));
    }
  };

  const login = async (email, password, rememberFor30Days = false) => {
    if (!isValidEmail(email)) { showNotice("error", "Enter a valid email address."); return; }
    try {
      const response = await apiClient.login(email, password);
      const nextToken = normalizeToken(response?.token);
      const normalizedEmail = normalizeEmail(email);
      if (!nextToken) { showNotice("error", "Login response did not include a valid token."); return; }
      setSessionToken(nextToken);
      setCurrentUserEmail(normalizedEmail);
      clearAuthStorage();
      if (rememberFor30Days) {
        writeJsonStorage(window.localStorage, PERSISTENT_AUTH_STORAGE_KEY, {
          token: nextToken,
          email: normalizedEmail,
          expiresAt: Date.now() + THIRTY_DAYS_MS
        });
      } else {
        writeJsonStorage(window.sessionStorage, SESSION_AUTH_STORAGE_KEY, {
          token: nextToken,
          email: normalizedEmail
        });
      }
      setActiveMenu("files");
      setNotice({ kind: "info", message: "" });
    } catch (error) {
      showNotice("error", normalizeAuthError(error, "login"));
    }
  };

  const logout = () => {
    clearAuthStorage();
    setSessionToken("");
    setCurrentUserEmail("");
    setActiveMenu("account");
    setFiles([]);
    setShares([]);
    setPreviewS3Key(null);
    showNotice("info", "Logged out.");
  };

  const refreshFiles = async () => {
    if (!token) { setFiles([]); return; }
    try {
      const result = await apiClient.listFiles(token);
      setFiles(result || []);
    } catch (error) { showNotice("error", error.message); }
  };

  const refreshShares = async () => {
    if (!token) { setShares([]); return; }
    try {
      const result = await apiClient.listShares(token);
      setShares(result || []);
    } catch (error) { showNotice("error", error.message); }
  };

  const uploadFile = async (file) => {
    if (!token) { showNotice("error", "Please login first."); return; }
    try {
      await apiClient.uploadFile(token, file);
      showNotice("success", "File uploaded.");
      await refreshFiles();
    } catch (error) { showNotice("error", error.message); }
  };

  const deleteFile = async (key) => {
    if (!token) { showNotice("error", "Please login first."); return; }
    try {
      await apiClient.deleteFile(token, key);
      showNotice("success", "File deleted.");
      await Promise.all([refreshFiles(), refreshShares()]);
    } catch (error) { showNotice("error", error.message); }
  };

  const renameFile = async (id, nextName) => {
    if (!token) { showNotice("error", "Please login first."); return; }
    try {
      await apiClient.renameFile(token, id, nextName);
      showNotice("success", "File renamed.");
      await Promise.all([refreshFiles(), refreshShares()]);
    } catch (error) { showNotice("error", error.message); }
  };

  const createShare = async (key) => {
    if (!token) { showNotice("error", "Please login first."); return; }
    const activeShareCount = shares.filter((share) => !share.revoked).length;
    if (activeShareCount >= MAX_ACTIVE_SHARE_LINKS) {
      showNotice("error", `Maximum ${MAX_ACTIVE_SHARE_LINKS} active share links allowed.`);
      return;
    }
    try {
      const result = await apiClient.createShare(token, key);
      showNotice("success", `Share created: ${result.publicDownloadUrl}`);
      await refreshShares();
    } catch (error) { showNotice("error", error.message); }
  };

  const revokeShare = async (id) => {
    if (!token) { showNotice("error", "Please login first."); return; }
    try {
      await apiClient.revokeShare(token, id);
      showNotice("success", "Share revoked.");
      await refreshShares();
    } catch (error) { showNotice("error", error.message); }
  };

  useEffect(() => {
    if (!token) { setFiles([]); setShares([]); setSearchQuery(""); return; }
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
      matchesSearch(file.originalName) || matchesSearch(file.s3Key) || matchesSearch(file.fileName)
    );
  }, [files, normalizedSearch]);

  const filteredShares = useMemo(() => {
    if (!normalizedSearch) return shares;
    return shares.filter((share) =>
      matchesSearch(share.key) || matchesSearch(share.publicDownloadUrl) || matchesSearch(share.token)
    );
  }, [shares, normalizedSearch]);

  const recentFiles = filteredFiles.slice(0, 6);
  const totalStorageBytes = files.reduce((sum, f) => sum + Number(f.sizeBytes || 0), 0);
  const initials = currentUserEmail
    ? currentUserEmail.split("@")[0].slice(0, 2).toUpperCase()
    : "GU";

  const menuItems = sessionActive
    ? [
        { key: "files", label: "My Storage", icon: <IconFiles /> },
        { key: "upload", label: "Upload", icon: <IconUpload /> },
        { key: "shares", label: "Share Links", icon: <IconShare /> },
      ]
    : [{ key: "account", label: "Account", icon: <IconAccount /> }];

  const handleMenuSelect = (key) => {
    if (!sessionActive && key !== "account") {
      showNotice("error", "Please login first.");
      setActiveMenu("account");
      return;
    }
    setNotice({ kind: "info", message: "" });
    setSearchQuery("");
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
            onPreview={(key) => setPreviewS3Key(key)}
          />
        );
      case "upload":
        return (
          <UploadPanel
            disabled={!sessionActive}
            onUpload={uploadFile}
            maxFilesPerAccount={MAX_FILES_PER_ACCOUNT}
            currentFileCount={files.length}
          />
        );
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
      topLeft={
        sessionActive ? (
          <div className="searchBox">
            <input
              type="search"
              placeholder="Search..."
              disabled={!sessionActive}
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
            />
          </div>
        ) : null
      }
      topRight={
        sessionActive ? (
          <ProfileDropdown
            email={currentUserEmail}
            initials={initials}
            onLogout={logout}
            onSwitchAccount={() => {
              logout();
              setActiveMenu("account");
            }}
          />
        ) : null
      }
      sideBottom={
        sessionActive ? (
          <StorageWidget usedBytes={totalStorageBytes} totalMB={STORAGE_CAPACITY_MB} />
        ) : null
      }
    >
      <AlertBar kind={notice.kind} message={notice.message} />

      {sessionActive && (
        <div className="demoBanner">
          Demo mode &mdash; max {MAX_FILES_PER_ACCOUNT} files per account &middot; max {MAX_ACTIVE_SHARE_LINKS} active share links &middot; {MAX_FILE_SIZE_MB} MB max per file &middot; upload rate limit: {UPLOAD_RATE_LIMIT_COUNT} files per {UPLOAD_RATE_LIMIT_WINDOW_MINUTES} minutes
        </div>
      )}

      {/* Page header with title + action buttons */}
      {activeMenu !== "account" && (
        <div className="pageHeader">
          <h1 className="pageTitle">{pageTitles[activeMenu] ?? "Dashboard"}</h1>
          {sessionActive && activeMenu === "files" && (
            <div className="pageActions">
              <button
                className="btnOutline"
                type="button"
                onClick={() => handleMenuSelect("upload")}
              >
                <IconUploadSmall /> Upload
              </button>
            </div>
          )}
        </div>
      )}

      {/* Stats cards */}
      {sessionActive && <StatsCards files={files} shares={shares} />}

      {/* Recent files strip */}
      {sessionActive && activeMenu === "files" && (
        <section className="recentStrip">
          <div className="recentStripHeader">
            <h2>Recent files</h2>
          </div>
          <div className="recentGrid">
            {recentFiles.length === 0 ? (
              <article className="recentCard">
                <div className="fileTypeIcon fileTypeIcon-lg fileTypeIcon-other">FILE</div>
                <strong>No files yet</strong>
              </article>
            ) : (
              recentFiles.map((file) => {
                const { label, cls } = getFileType(file.mimeType, file.originalName || file.fileName);
                return (
                  <article
                    className="recentCard"
                    key={file.id || file.s3Key}
                    onClick={() => setPreviewS3Key(file.s3Key)}
                    style={{ cursor: "pointer" }}
                  >
                    <div className={`fileTypeIcon fileTypeIcon-lg ${cls}`}>{label}</div>
                    <strong>{file.originalName || "Untitled file"}</strong>
                  </article>
                );
              })
            )}
          </div>
        </section>
      )}

      {/* Main view */}
      <section className="workspace workspaceSingle">
        <div className="stack mainColumn">{renderMenuView()}</div>
      </section>

      {/* File preview modal */}
      {previewS3Key && (
        <FilePreviewModal
          token={token}
          s3Key={previewS3Key}
          onClose={() => setPreviewS3Key(null)}
          onStatus={showNotice}
        />
      )}
    </DashboardLayout>
  );
}

export default DashboardPage;
