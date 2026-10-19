import { useEffect, useRef, useState } from "react";
import { completeTemporaryPassword, login, verifyWebAuthnLogin } from "../api/auth";
import logo from "../assets/logo.png";
import PasswordInput from "../components/PasswordInput";

function Login({ onLogin }) {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [message, setMessage] = useState("");
    const [suspension, setSuspension] = useState(null);
    const [loading, setLoading] = useState(false);
    const [challenge, setChallenge] = useState(null);
    const [passwordChangeRequired, setPasswordChangeRequired] = useState(false);
    const [temporaryPassword, setTemporaryPassword] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [now, setNow] = useState(() => Date.now());
    const submitting = useRef(false);
    const expired = challenge && now >= Date.parse(challenge.expiresAt);

    useEffect(() => {
        if (!challenge) return;
        const timer = setInterval(() => setNow(Date.now()), 1000);
        return () => clearInterval(timer);
    }, [challenge]);

    function startAgain() {
        if (submitting.current) return;
        setChallenge(null);
        setPasswordChangeRequired(false);
        setTemporaryPassword("");
        setNewPassword("");
        setConfirmPassword("");
        setPassword("");
        setMessage("");
        setSuspension(null);
    }

    async function handleSubmit(e) {
        e.preventDefault();
        if (submitting.current) return;
        if (challenge && Date.now() >= Date.parse(challenge.expiresAt)) {
            setNow(Date.now());
            return;
        }

        setMessage("");
        setLoading(true);
        submitting.current = true;

        try {
            if (passwordChangeRequired) {
                if (newPassword !== confirmPassword) {
                    setMessage("New passwords do not match.");
                    return;
                }
                await completeTemporaryPassword(username, temporaryPassword, newPassword);
            } else if (challenge) {
                await verifyWebAuthnLogin(challenge.challengeToken);
            } else {
                const result = await login(username, password);
                if (result.passwordChangeRequired) {
                    setTemporaryPassword(password);
                    setPasswordChangeRequired(true);
                    return;
                }
                if (result.mfaRequired) {
                    setNow(Date.now());
                    setChallenge(result);
                    return;
                }
            }
            onLogin();
        } catch (error) {
            if (error.code === "ACCOUNT_SUSPENDED") {
                const until = error.suspendedUntil ? new Date(error.suspendedUntil) : null;
                setSuspension({ until: until && Number.isFinite(until.getTime()) ? until : null });
            } else if (error.status === 429) {
                setMessage("Too many sign-in attempts. Wait 15 minutes before trying again.");
            } else if (challenge && error.status === 401) {
                setMessage("Verification failed or expired. Start again to sign in.");
            } else {
                setMessage(error.message || "Login failed");
            }
        } finally {
            setPassword("");
            setLoading(false);
            submitting.current = false;
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
                    {suspension ? (
                        <section className="login-suspended-view" aria-labelledby="suspended-title">
                            <button type="button" className="login-suspended-back" onClick={() => { setSuspension(null); setMessage(""); setPassword(""); }}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m15 18-6-6 6-6" /></svg>Back to sign in</button>
                            <div className="login-suspended-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="9" /><path d="M9 8v8M15 8v8" /></svg></div>
                            <span className="login-suspended-eyebrow">Account access paused</span>
                            <h1 id="suspended-title">{suspension.until ? "Your account is temporarily suspended" : "Your account is suspended"}</h1>
                            <p>Your files remain safely stored, but you cannot access File Drive while this suspension is active.</p>
                            <div className="login-suspended-status">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></svg>
                                <span><small>{suspension.until ? "Access automatically resumes" : "Suspension duration"}</small><strong>{suspension.until ? suspension.until.toLocaleString([], { dateStyle: "long", timeStyle: "short" }) : "Indefinite"}</strong></span>
                            </div>
                            <p className="login-suspended-help">If you believe this is a mistake or need access sooner, contact your administrator.</p>
                            <button type="button" className="login-suspended-primary" onClick={() => { setSuspension(null); setMessage(""); setPassword(""); }}>Back to sign in</button>
                        </section>
                    ) : <>
                    <div className="login-form-heading">
                        <span>{passwordChangeRequired ? "First sign in" : challenge ? "Two-step sign in" : "Welcome back"}</span>
                        <h1>{passwordChangeRequired ? "Choose your password" : challenge ? "Verify your identity" : "Sign in to File Drive"}</h1>
                        <p>{passwordChangeRequired
                            ? "Replace the temporary password with a password only you know."
                            : challenge
                            ? "Use your registered security key or passkey to continue."
                            : "Enter your account details to continue."}</p>
                    </div>

                    <form className="login-form" onSubmit={handleSubmit}>
                        {passwordChangeRequired ? (
                            <>
                                <p className="login-mfa-account">Setting a password for <strong>{username}</strong></p>
                                <label htmlFor="new-password">New password</label>
                                <PasswordInput
                                    id="new-password"
                                    autoComplete="new-password"
                                    minLength="8"
                                    required
                                    disabled={loading}
                                    value={newPassword}
                                    onChange={(event) => setNewPassword(event.target.value)}
                                />
                                <label htmlFor="confirm-password">Confirm new password</label>
                                <PasswordInput
                                    id="confirm-password"
                                    autoComplete="new-password"
                                    minLength="8"
                                    required
                                    disabled={loading}
                                    value={confirmPassword}
                                    onChange={(event) => setConfirmPassword(event.target.value)}
                                />
                            </>
                        ) : challenge ? (
                            <>
                                <p className="login-mfa-account">Signing in as <strong>{username}</strong></p>
                                {expired && <p className="message error login-error" role="alert">
                                    This sign-in request has expired. Start again to request a new one.
                                </p>}
                            </>
                        ) : (
                            <>
                        <label htmlFor="username">Username</label>
                        <input
                            id="username"
                            type="text"
                            placeholder="Enter your username"
                            autoComplete="username"
                            required
                            disabled={loading}
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                        />

                        <label htmlFor="password">Password</label>
                        <PasswordInput
                            id="password"
                            placeholder="Enter your password"
                            autoComplete="current-password"
                            required
                            disabled={loading}
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                        />
                            </>
                        )}

                        {message && <p className="message error login-error" role="alert">{message}</p>}

                        <button type="submit" disabled={loading || Boolean(expired)}>
                            {loading
                                ? (passwordChangeRequired ? "Saving..." : challenge ? "Verifying..." : "Signing in...")
                                : (passwordChangeRequired ? "Set password and sign in" : challenge ? "Use security key or passkey" : "Sign in")}
                        </button>
                        {(challenge || passwordChangeRequired) && <button type="button" className="login-start-again" disabled={loading} onClick={startAgain}>
                            Start again
                        </button>}
                    </form>

                    <p className="login-security-note">
                        Your session is protected with secure authentication.
                    </p>
                    </>}
                </div>
            </section>
        </main>
    );
}

export default Login;
