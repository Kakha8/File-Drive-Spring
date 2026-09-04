import { useEffect, useRef, useState } from "react";
import { login, verifyTotpLogin } from "../api/auth";
import logo from "../assets/logo.png";

function Login({ onLogin }) {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [message, setMessage] = useState("");
    const [loading, setLoading] = useState(false);
    const [challenge, setChallenge] = useState(null);
    const [code, setCode] = useState("");
    const [now, setNow] = useState(() => Date.now());
    const codeInput = useRef(null);
    const submitting = useRef(false);
    const expired = challenge && now >= Date.parse(challenge.expiresAt);

    useEffect(() => {
        if (!challenge) return;
        codeInput.current?.focus();
        const timer = setInterval(() => setNow(Date.now()), 1000);
        return () => clearInterval(timer);
    }, [challenge]);

    function startAgain() {
        if (submitting.current) return;
        setChallenge(null);
        setCode("");
        setPassword("");
        setMessage("");
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
            if (challenge) {
                await verifyTotpLogin(challenge.challengeToken, code);
                setCode("");
            } else {
                const result = await login(username, password);
                if (result.mfaRequired) {
                    setNow(Date.now());
                    setChallenge(result);
                    return;
                }
            }
            onLogin();
        } catch (error) {
            if (error.status === 429) {
                setMessage("Too many sign-in attempts. Wait 15 minutes before trying again.");
            } else if (challenge && error.status === 401) {
                setMessage("That code is invalid, already used, or the request has expired. Try the next code, or start again.");
            } else {
                setMessage(error.message || "Login failed");
            }
            setCode("");
            codeInput.current?.focus();
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
                    <div className="login-form-heading">
                        <span>{challenge ? "Two-step sign in" : "Welcome back"}</span>
                        <h1>{challenge ? "Verify your identity" : "Sign in to File Drive"}</h1>
                        <p>{challenge
                            ? "Enter the six-digit code shown on your authenticator device."
                            : "Enter your account details to continue."}</p>
                    </div>

                    <form className="login-form" onSubmit={handleSubmit}>
                        {challenge ? (
                            <>
                                <p className="login-mfa-account">Signing in as <strong>{username}</strong></p>
                                <label htmlFor="totp-code">Authenticator code</label>
                                <input
                                    ref={codeInput}
                                    id="totp-code"
                                    className="login-totp-code"
                                    type="text"
                                    inputMode="numeric"
                                    autoComplete="one-time-code"
                                    pattern="[0-9]{6}"
                                    maxLength={6}
                                    placeholder="000000"
                                    required
                                    disabled={loading || expired}
                                    value={code}
                                    aria-describedby="totp-help"
                                    onChange={(e) => setCode(e.target.value.replace(/[^0-9]/g, "").slice(0, 6))}
                                />
                                <p id="totp-help" className="login-mfa-help">
                                    Use a fresh code. Codes used to register the device cannot be reused.
                                </p>
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
                        <input
                            id="password"
                            type="password"
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

                        <button type="submit" disabled={loading || Boolean(expired) || (challenge && code.length !== 6)}>
                            {loading ? (challenge ? "Verifying..." : "Signing in...") : (challenge ? "Verify and sign in" : "Sign in")}
                        </button>
                        {challenge && <button type="button" className="login-start-again" disabled={loading} onClick={startAgain}>
                            Start again
                        </button>}
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
