import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getCurrentUsername } from "../api/auth";
import { getTotpStatus, removeTotpDevice } from "../api/totp";
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
    const [removingId, setRemovingId] = useState(null);
    const [password, setPassword] = useState("");
    const [code, setCode] = useState("");
    const [authorizingDeviceId, setAuthorizingDeviceId] = useState("");
    const [removalError, setRemovalError] = useState("");
    const [removing, setRemoving] = useState(false);
    const removingDevice = totpStatus?.devices.find((device) => device.deviceId === removingId);

    useEffect(() => {
        let active = true;
        getTotpStatus()
            .then((status) => active && setTotpStatus(status))
            .catch(() => active && setTotpError(true));
        return () => { active = false; };
    }, []);

    function beginRemoval(deviceId) {
        const alternative = totpStatus.devices.find((device) => device.deviceId !== deviceId);
        setRemovingId(deviceId);
        setAuthorizingDeviceId(alternative ? String(alternative.deviceId) : String(deviceId));
        setPassword("");
        setCode("");
        setRemovalError("");
    }

    async function submitRemoval(event) {
        event.preventDefault();
        setRemoving(true);
        setRemovalError("");
        try {
            await removeTotpDevice(removingId, password, Number(authorizingDeviceId), code);
            setTotpStatus(await getTotpStatus());
            setRemovingId(null);
            setPassword("");
            setCode("");
        } catch (error) {
            setRemovalError(error.message || "Could not remove the device.");
            setPassword("");
            setCode("");
        } finally {
            setRemoving(false);
        }
    }

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
                                    <button type="button" className="settings-device-remove" onClick={() => beginRemoval(device.deviceId)}>
                                        Remove
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}

                    {removingId !== null && (
                        <form className="settings-removal-form" onSubmit={submitRemoval}>
                            <strong>Remove “{removingDevice?.displayName || "this device"}”?</strong>
                            <p>Confirm with your password and a fresh authenticator code.</p>
                            {totpStatus.devices.length > 1 && (
                                <>
                                    <label htmlFor="removal-authorizer">Confirm using another device</label>
                                    <select id="removal-authorizer" value={authorizingDeviceId}
                                        onChange={(event) => setAuthorizingDeviceId(event.target.value)} disabled={removing}>
                                        {totpStatus.devices.filter((device) => device.deviceId !== removingId).map((device) => (
                                            <option key={device.deviceId} value={device.deviceId}>{device.displayName}</option>
                                        ))}
                                    </select>
                                </>
                            )}
                            <label htmlFor="removal-password">Current password</label>
                            <input id="removal-password" type="password" autoComplete="current-password" required
                                value={password} onChange={(event) => setPassword(event.target.value)} disabled={removing} />
                            <label htmlFor="removal-code">Authenticator code</label>
                            <input id="removal-code" type="text" inputMode="numeric" autoComplete="one-time-code"
                                pattern="[0-9]{6}" maxLength={6} required value={code} disabled={removing}
                                onChange={(event) => setCode(event.target.value.replace(/[^0-9]/g, "").slice(0, 6))} />
                            {removalError && <p className="message error" role="alert">{removalError}</p>}
                            <div className="settings-removal-actions">
                                <button type="button" onClick={() => setRemovingId(null)} disabled={removing}>Cancel</button>
                                <button type="submit" className="danger" disabled={removing || code.length !== 6}>
                                    {removing ? "Removing…" : "Remove device"}
                                </button>
                            </div>
                        </form>
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
