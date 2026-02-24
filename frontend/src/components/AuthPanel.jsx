import { useState } from "react";
import { Link } from "react-router-dom";

function AuthPanel({ sessionActive, onRegister, onLogin, onLogout, registerCooldownSeconds = 0 }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const registerLocked = registerCooldownSeconds > 0;

  const submit = (handler) => {
    if (!email.trim() || !password) return;
    handler(email.trim(), password);
  };

  return (
    <section className={`card authCard ${sessionActive ? "authLoggedIn" : "authGuest"}`}>
      {sessionActive ? (
        <div>
          <h2>Account</h2>
          <div className="actions">
            <p className="muted">You are logged in.</p>
            <button className="ghost" onClick={onLogout}>
              Logout
            </button>
          </div>
        </div>
      ) : (
        <div className="authGuestBody">
          <h2 className="authTitle">Welcome Back !</h2>
          <p className="authDemoNote">
            Welcome to FileShare. This live demo showcases secure file upload, sharing, and download workflows built with Spring Boot, React, AWS S3, and PostgreSQL. Demo limits apply to upload size and file count per account.
          </p>
          <div className="authDemoCredentials">
            <p>
              If you prefer not to use your personal email, you can sign in with the demo account below to explore the app.
            </p>
            <p><strong>Email:</strong> cloudshare0222@gmail.com</p>
            <p><strong>Password:</strong> Ilovecloudshare</p>
            <p>
              If you want to look at the source code, please visit{" "}
              <a
                className="authRepoLink"
                href="https://github.com/lawrence900613/CloudShare"
                target="_blank"
                rel="noreferrer"
              >
                here
              </a>
            </p>
          </div>
          <div className="fieldGroup authFieldGroup">
            <label>
              Email address
              <input
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                type="email"
                pattern="[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"
                title="Enter a valid email address"
                placeholder="Email address"
              />
            </label>
            <label>
              Password
              <input
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                type="password"
                placeholder="Password"
              />
            </label>
          </div>
          <div className="authMetaRow">
            <Link className="authForgotLink" to="/forgot-password">
              Forgot password
            </Link>
          </div>
          <div className="actions authActions">
            <button className="authPrimaryBtn" onClick={() => submit(onLogin)}>
              Login
            </button>
            <button className="ghost authSecondaryBtn" disabled={registerLocked} onClick={() => submit(onRegister)}>
              {registerLocked ? `Register (${registerCooldownSeconds}s)` : "Sign up"}
            </button>
          </div>
        </div>
      )}
    </section>
  );
}

export default AuthPanel;
