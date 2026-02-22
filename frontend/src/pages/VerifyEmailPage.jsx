import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import DashboardLayout from "../layout/DashboardLayout";
import AlertBar from "../components/AlertBar";
import { apiClient } from "../util/apiClient";

function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const [state, setState] = useState({
    kind: "info",
    title: "Checking Your Link",
    message: "We are verifying your email address...",
    showRegisterHint: false
  });
  const hasRequestedRef = useRef(false);

  useEffect(() => {
    if (hasRequestedRef.current) return;
    hasRequestedRef.current = true;

    const token = searchParams.get("token");
    if (!token) {
      setState({
        kind: "error",
        title: "Verification Failed",
        message: "Verification token is missing. Please register again.",
        showRegisterHint: true
      });
      return;
    }

    apiClient
      .verifyEmail(token)
      .then((payload) => {
        setState({
          kind: "success",
          title: "Email Verified",
          message: payload.message || "Email verified. You can login now.",
          showRegisterHint: false
        });
      })
      .catch((error) => {
        const message = (error.message || "").toLowerCase();
        if (message.includes("expired")) {
          setState({
            kind: "error",
            title: "Link Expired",
            message: "Verification link expired. Please register again.",
            showRegisterHint: true
          });
          return;
        }
        if (message.includes("already used")) {
          setState({
            kind: "success",
            title: "Already Verified",
            message: "Email is already verified. You can login now.",
            showRegisterHint: false
          });
          return;
        }
        if (message.includes("invalid")) {
          setState({
            kind: "error",
            title: "Invalid Link",
            message: "This verification link is invalid. Please register again.",
            showRegisterHint: true
          });
          return;
        }
        setState({
          kind: "error",
          title: "Verification Failed",
          message: error.message || "Could not verify email. Please register again.",
          showRegisterHint: true
        });
      });
  }, [searchParams]);

  return (
    <DashboardLayout>
      <section className={`card verifyCard verifyCard-${state.kind}`}>
        <p className="verifyBadge">Email Verification</p>
        <h2>{state.title}</h2>
        <AlertBar kind={state.kind} message={state.message} />
        {state.showRegisterHint && (
          <p className="verifyHint">
            Open the dashboard and register with your email to receive a new verification link.
          </p>
        )}
        <div className="actions">
          <Link className="linkButton" to="/">
            Back To Dashboard
          </Link>
        </div>
      </section>
    </DashboardLayout>
  );
}

export default VerifyEmailPage;
