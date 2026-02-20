import { useEffect, useState } from "react";
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
  const [files, setFiles] = useState([]);
  const [shares, setShares] = useState([]);
  const [notice, setNotice] = useState({ kind: "info", message: "" });

  const sessionActive = Boolean(token);
  const emailPattern = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
  const normalizeToken = (value) => (typeof value === "string" ? value.trim() : "");

  const showNotice = (kind, message) => setNotice({ kind, message });

  const isValidEmail = (email) => emailPattern.test(String(email || "").trim());

  const register = async (email, password) => {
    if (!isValidEmail(email)) {
      showNotice("error", "Enter a valid email address.");
      return;
    }
    try {
      const response = await apiClient.register(email, password);
      showNotice("success", response.message || "Registration successful.");
    } catch (error) {
      showNotice("error", error.message);
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
      showNotice("success", "Login successful.");
    } catch (error) {
      showNotice("error", error.message);
    }
  };

  const logout = () => {
    setSessionToken("");
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
      return;
    }
    refreshFiles();
    refreshShares();
  }, [token]);

  return (
    <DashboardLayout>
      <p className="backendLabel">
        Backend <code>{apiClient.baseUrl}</code>
      </p>
      <AlertBar kind={notice.kind} message={notice.message} />

      {sessionActive && <StatsCards files={files} shares={shares} />}

      <section className="workspace">
        <div className="stack">
          <AuthPanel
            sessionActive={sessionActive}
            onRegister={register}
            onLogin={login}
            onLogout={logout}
          />
          <UploadPanel disabled={!sessionActive} onUpload={uploadFile} />
        </div>

        <div className="stack">
          <FilesTable
            sessionActive={sessionActive}
            token={token}
            files={files}
            onRefresh={refreshFiles}
            onDelete={deleteFile}
            onRename={renameFile}
            onCreateShare={createShare}
            onStatus={showNotice}
          />
          <SharesTable
            sessionActive={sessionActive}
            shares={shares}
            onRefresh={refreshShares}
            onRevoke={revokeShare}
            onStatus={showNotice}
          />
        </div>
      </section>
    </DashboardLayout>
  );
}

export default DashboardPage;
