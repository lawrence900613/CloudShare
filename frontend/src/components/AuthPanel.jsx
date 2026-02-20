import { useState } from "react";

function AuthPanel({ sessionActive, onRegister, onLogin, onLogout }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

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
          <h2>Account</h2>
          <div className="fieldGroup two">
            <label>
              Email
              <input
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                type="email"
                pattern="[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"
                title="Enter a valid email address"
                placeholder="user@example.com"
              />
            </label>
            <label>
              Password
              <input
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                type="password"
                placeholder="minimum 6 chars"
              />
            </label>
          </div>
          <div className="actions">
            <button onClick={() => submit(onRegister)}>Register</button>
            <button onClick={() => submit(onLogin)}>Login</button>
          </div>
        </div>
      )}
    </section>
  );
}

export default AuthPanel;
