import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import DashboardLayout from "../layout/DashboardLayout";
import AlertBar from "../components/AlertBar";
import { apiClient } from "../util/apiClient";

function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const [state, setState] = useState({ kind: "info", message: "Verifying..." });
  const hasRequestedRef = useRef(false);

  useEffect(() => {
    if (hasRequestedRef.current) return;
    hasRequestedRef.current = true;

    const token = searchParams.get("token");
    if (!token) {
      setState({ kind: "error", message: "Verification token is missing." });
      return;
    }

    apiClient
      .verifyEmail(token)
      .then((payload) => {
        setState({ kind: "success", message: payload.message || "Email verified." });
      })
      .catch((error) => {
        const message = (error.message || "").toLowerCase();
        if (message.includes("already used")) {
          setState({
            kind: "success",
            message: "Email is already verified. You can login now."
          });
          return;
        }
        setState({ kind: "error", message: error.message });
      });
  }, [searchParams]);

  return (
    <DashboardLayout>
      <section className="card verifyCard">
        <h2>Email Verification</h2>
        <AlertBar kind={state.kind} message={state.message} />
        <Link className="linkButton" to="/">
          Back To Dashboard
        </Link>
      </section>
    </DashboardLayout>
  );
}

export default VerifyEmailPage;
