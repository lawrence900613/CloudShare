import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import DashboardLayout from "../layout/DashboardLayout";
import AlertBar from "../components/AlertBar";
import { apiClient } from "../util/apiClient";

function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [notice, setNotice] = useState({ kind: "info", message: "" });
  const [loading, setLoading] = useState(false);
  const [cooldownSeconds, setCooldownSeconds] = useState(0);
  const cooldownActive = cooldownSeconds > 0;

  const onSubmit = async (event) => {
    event.preventDefault();
    if (cooldownActive) {
      setNotice({ kind: "info", message: `Please wait ${cooldownSeconds}s before sending again.` });
      return;
    }
    if (!email.trim()) {
      setNotice({ kind: "error", message: "Email is required." });
      return;
    }
    setLoading(true);
    try {
      const response = await apiClient.forgotPassword(email.trim());
      setNotice({
        kind: "success",
        message: response?.message || "If the account exists, a password reset email has been sent."
      });
      setCooldownSeconds(10);
    } catch (error) {
      setNotice({ kind: "error", message: error.message || "Request failed." });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (cooldownSeconds <= 0) return undefined;
    const timer = setInterval(() => {
      setCooldownSeconds((current) => (current <= 1 ? 0 : current - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [cooldownSeconds]);

  return (
    <DashboardLayout>
      <section className="card verifyCard verifyCard-info">
        <p className="verifyBadge">Password Reset</p>
        <h2>Forgot Your Password?</h2>
        <p className="verifyHint">Enter your email and we will send you a reset link.</p>
        <form className="fieldGroup" onSubmit={onSubmit}>
          <label>
            Email
            <input
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              type="email"
              placeholder="user@example.com"
            />
          </label>
          <div className="actions">
            <button type="submit" disabled={loading || cooldownActive}>
              {loading ? "Sending..." : cooldownActive ? `Send Reset Link (${cooldownSeconds}s)` : "Send Reset Link"}
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

export default ForgotPasswordPage;
