import { useState } from "react";
import { login } from "../api/auth";
import logo from "../assets/logo.png";

function Login({ onLogin }) {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [message, setMessage] = useState("");
    const [loading, setLoading] = useState(false);

    async function handleSubmit(e) {
        e.preventDefault();

        setMessage("");
        setLoading(true);

        try {
            await login(username, password);
            onLogin();
        } catch (error) {
            setMessage(error.message || "Login failed");
        } finally {
            setLoading(false);
        }
    }

    return (
        <main className="page login-page">
            <section className="login-card login-split-card">
                <div className="login-brand-panel">
                    <div className="login-brand-glow" aria-hidden="true" />

                    <div className="login-brand-name">
                        <img src={logo} alt="" />
                        <span>File Drive</span>
                    </div>

                    <div className="login-brand-visual">
                        <img src={logo} alt="File Drive" />
                    </div>

                    <div className="login-brand-copy">
                        <p>Secure storage, made simple.</p>
                        <span>Keep your files protected, organized, and available wherever you work.</span>
                    </div>
                </div>

                <div className="login-form-panel">
                    <div className="login-form-heading">
                        <span>Welcome back</span>
                        <h1>Sign in to File Drive</h1>
                        <p>Enter your account details to continue.</p>
                    </div>

                    <form className="login-form" onSubmit={handleSubmit}>
                        <label htmlFor="username">Username</label>
                        <input
                            id="username"
                            type="text"
                            placeholder="Enter your username"
                            autoComplete="username"
                            required
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                        />

                        <label htmlFor="password">Password</label>
                        <input
                            id="password"
                            type="password"
                            placeholder="Enter your password"
                            autoComplete="current-password"
                            required
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                        />

                        {message && <p className="message error login-error" role="alert">{message}</p>}

                        <button type="submit" disabled={loading}>
                            {loading ? "Signing in..." : "Sign in"}
                        </button>
                    </form>

                    <p className="login-security-note">
                        Your session is protected with secure authentication.
                    </p>
                </div>
            </section>
        </main>
    );
}

export default Login;
