import { useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import DashboardLayout from "../layout/DashboardLayout";
import AlertBar from "../components/AlertBar";
import { apiClient } from "../util/apiClient";

function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = useMemo(() => searchParams.get("token") || "", [searchParams]);
  const [password, setPassword] = useState("");
  const [notice, setNotice] = useState({ kind: "info", message: "" });
  const [loading, setLoading] = useState(false);

  const onSubmit = async (event) => {
    event.preventDefault();
    if (!token) {
      setNotice({ kind: "error", message: "Reset token is missing. Please request a new reset link." });
      return;
    }
    if (password.length < 6) {
      setNotice({ kind: "error", message: "Password must be at least 6 characters." });
      return;
    }

    setLoading(true);
    try {
      const response = await apiClient.resetPassword(token, password);
      setNotice({
        kind: "success",
        message: response?.message || "Password reset successful. Please login with your new password."
      });
      setPassword("");
    } catch (error) {
      const lowered = String(error.message || "").toLowerCase();
      if (lowered.includes("expired")) {
        setNotice({ kind: "error", message: "Reset link expired. Please request a new one." });
      } else {
        setNotice({ kind: "error", message: error.message || "Could not reset password." });
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <DashboardLayout>
      <section className="card verifyCard verifyCard-info">
        <p className="verifyBadge">Password Reset</p>
        <h2>Set New Password</h2>
        <form className="fieldGroup" onSubmit={onSubmit}>
          <label>
            New Password
            <input
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              type="password"
              placeholder="minimum 6 chars"
            />
          </label>
          <div className="actions">
            <button type="submit" disabled={loading}>
              {loading ? "Updating..." : "Reset Password"}
            </button>
            <Link className="linkButton ghostLinkButton" to="/">
              Back To Dashboard
            </Link>
          </div>
        </form>
        <AlertBar kind={notice.kind} message={notice.message} />
      </section>
    </DashboardLayout>
  );
}

export default ResetPasswordPage;
