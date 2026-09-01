import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getCurrentUsername } from "../api/auth";
import { getTotpStatus } from "../api/totp";
import UserMenu from "../components/UserMenu";

function getInitials(username) {
    const parts = username
        .trim()
        .split(/[\s._-]+/)
        .filter(Boolean);

    if (parts.length >= 2) {
        return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
    }

    return (parts[0] || "U").slice(0, 2).toUpperCase();
}

export default function Settings({ onLogout }) {
    const navigate = useNavigate();
    const username = getCurrentUsername();
    const [totpStatus, setTotpStatus] = useState(null);
    const [totpError, setTotpError] = useState(false);

    useEffect(() => {
        let active = true;
        getTotpStatus()
            .then((status) => active && setTotpStatus(status))
            .catch(() => active && setTotpError(true));
        return () => { active = false; };
    }, []);

    return (
        <main className="settings-page">
            <header className="settings-topbar">
                <button
                    type="button"
                    className="settings-back-button"
                    onClick={() => navigate(-1)}
                >
                    ← Back
                </button>

                <UserMenu onLogout={onLogout} />
            </header>

            <section className="settings-content">
                <p className="settings-eyebrow">Workspace</p>
                <h1>Settings</h1>
                <p className="settings-description">
                    Manage your account and application preferences.
                </p>

                <section className="settings-card">
                    <h2>Account</h2>

                    <div className="settings-account">
                        <span className="settings-account-avatar">
                            {getInitials(username)}
                        </span>

                        <div>
                            <strong>{username}</strong>
                            <span>Signed-in account</span>
                        </div>
                    </div>

                    <div className="settings-security-row">
                        <div>
                            <strong>Two-factor authentication</strong>
                            <span>Authenticator app verification during sign in</span>
                        </div>
                        {totpError ? (
                            <span className="settings-status settings-status-error">Unavailable</span>
                        ) : totpStatus === null ? (
                            <span className="settings-status">Loading…</span>
                        ) : (
                            <span className={`settings-status ${totpStatus.enabled ? "settings-status-enabled" : "settings-status-disabled"}`}>
                                {totpStatus.enabled ? "Enabled" : "Disabled"}
                            </span>
                        )}
                    </div>

                    {totpStatus?.devices.length > 0 && (
                        <div className="settings-device-list">
                            {totpStatus.devices.map((device, index) => (
                                <div className="settings-device" key={`${device.displayName}-${index}`}>
                                    <span className="settings-device-icon" aria-hidden="true">◈</span>
                                    <div>
                                        <strong>{device.displayName}</strong>
                                        <span>Hardware wallet</span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </section>

                <section className="settings-card">
                    <h2>Appearance</h2>
                    <p>The application is currently using the dark theme.</p>
                </section>
            </section>
        </main>
    );
}
