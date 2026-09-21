import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";
import { getCurrentUsername } from "../api/auth";
import UserMenu from "../components/UserMenu";
import SecurityKeySettings from "../components/SecurityKeySettings";
import SecurityKeyIcon from "../components/SecurityKeyIcon";
import "../components/SecurityKeySettings.css";
import { getSecurityKeyStatus, removeSecurityKey } from "../api/webauthn";

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
    const [securityKeyStatus, setSecurityKeyStatus] = useState(null);
    const [securityKeyError, setSecurityKeyError] = useState(false);
    const [removingKeyId, setRemovingKeyId] = useState(null);
    const [keyRemovalPassword, setKeyRemovalPassword] = useState("");
    const [keyRemovalMessage, setKeyRemovalMessage] = useState("");
    const [keyRemovalError, setKeyRemovalError] = useState("");
    const [keyRemoving, setKeyRemoving] = useState(false);
    const keyRemovalDialog = useRef(null);
    const keyRemovalTrigger = useRef(null);

    useEffect(() => {
        if (removingKeyId === null) return undefined;
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = "hidden";
        keyRemovalDialog.current?.querySelector("input")?.focus();
        function onKeyDown(event) {
            if (event.key === "Escape" && !keyRemoving) {
                event.preventDefault();
                setRemovingKeyId(null);
                requestAnimationFrame(() => keyRemovalTrigger.current?.focus());
            }
            if (event.key !== "Tab") return;
            const controls = [...(keyRemovalDialog.current?.querySelectorAll("button:not(:disabled), input:not(:disabled), select:not(:disabled)") || [])];
            if (!controls.length) return;
            const first = controls[0], last = controls[controls.length - 1];
            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
        }
        document.addEventListener("keydown", onKeyDown);
        return () => { document.body.style.overflow = previousOverflow; document.removeEventListener("keydown", onKeyDown); };
    }, [removingKeyId, keyRemoving]);

    useEffect(() => {
        let active = true;
        getSecurityKeyStatus()
            .then((status) => active && setSecurityKeyStatus(status))
            .catch(() => {
                if (active) { setSecurityKeyError(true); setSecurityKeyStatus({ enabled: false, devices: [] }); }
            });
        return () => { active = false; };
    }, []);

    function beginKeyRemoval(credentialRecordId) {
        keyRemovalTrigger.current = document.activeElement;
        setRemovingKeyId(credentialRecordId);
        setKeyRemovalPassword("");
        setKeyRemovalMessage("");
        setKeyRemovalError("");
    }

    function closeKeyRemoval() {
        if (keyRemoving) return;
        setRemovingKeyId(null);
        setKeyRemovalPassword("");
        setKeyRemovalMessage("");
        setKeyRemovalError("");
        requestAnimationFrame(() => keyRemovalTrigger.current?.focus());
    }

    async function submitKeyRemoval(event) {
        event.preventDefault();
        setKeyRemoving(true);
        setKeyRemovalError("");
        setKeyRemovalMessage("Preparing removal…");
        try {
            await removeSecurityKey(removingKeyId, keyRemovalPassword, setKeyRemovalMessage);
            onLogout();
        } catch (error) {
            setKeyRemovalMessage("");
            setKeyRemovalError(error.message || "Could not remove the security key.");
        } finally {
            setKeyRemovalPassword("");
            setKeyRemoving(false);
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
                            <strong>FIDO2 authentication</strong>
                            <span>Security keys and passkeys used during sign in</span>
                        </div>
                        {securityKeyError ? (
                            <span className="settings-status settings-status-error">Unavailable</span>
                        ) : securityKeyStatus === null ? (
                            <span className="settings-status">Loading…</span>
                        ) : (
                            <span className={`settings-status ${securityKeyStatus.enabled ? "settings-status-enabled" : "settings-status-disabled"}`}>
                                {securityKeyStatus.enabled ? "Enabled" : "Disabled"}
                            </span>
                        )}
                    </div>

                    {securityKeyStatus?.devices.length > 0 && (
                        <div className="settings-device-list">
                            {securityKeyStatus.devices.map((device) => (
                                <div className="settings-device" key={device.credentialRecordId}>
                                    <span className="settings-device-icon" aria-hidden="true"><SecurityKeyIcon /></span>
                                    <div>
                                        <strong>{device.displayName}</strong>
                                        <span>FIDO2 security key · Registered {new Date(device.createdAt).toLocaleDateString()}
                                            {device.lastUsedAt ? ` · Last used ${new Date(device.lastUsedAt).toLocaleDateString()}` : " · Never used"}
                                        </span>
                                    </div>
                                    <button type="button" className="settings-device-remove"
                                        onClick={() => beginKeyRemoval(device.credentialRecordId)}>Remove</button>
                                </div>
                            ))}
                        </div>
                    )}

                </section>

                <SecurityKeySettings onLogout={onLogout} />

                <section className="settings-card">
                    <h2>Appearance</h2>
                    <p>The application is currently using the dark theme.</p>
                </section>
            </section>
            {removingKeyId !== null && createPortal(
                <div className="security-key-modal-backdrop" onMouseDown={(event) => {
                    if (event.target === event.currentTarget) closeKeyRemoval();
                }}>
                    <section ref={keyRemovalDialog} className="security-key-modal security-key-removal-modal"
                        role="dialog" aria-modal="true" aria-labelledby="key-removal-title"
                        aria-describedby="key-removal-description">
                        <div className="security-key-modal-header">
                            <span className="security-key-modal-icon" aria-hidden="true"><SecurityKeyIcon /></span>
                            <div>
                                <h2 id="key-removal-title">Remove “{securityKeyStatus?.devices.find(device => device.credentialRecordId === removingKeyId)?.displayName || "this security key"}”?</h2>
                                <p id="key-removal-description">This key will no longer verify sign-ins for your account.</p>
                            </div>
                            <button type="button" className="security-key-modal-close" aria-label="Close" disabled={keyRemoving} onClick={closeKeyRemoval}>×</button>
                        </div>
                        <form className="security-key-modal-form" onSubmit={submitKeyRemoval}>
                            <p className="security-key-modal-note">Confirm with your account password and a registered security key.</p>
                            <label htmlFor="key-removal-password">Current password</label>
                            <input id="key-removal-password" type="password" autoComplete="current-password" required
                                value={keyRemovalPassword} disabled={keyRemoving}
                                onChange={(event) => setKeyRemovalPassword(event.target.value)} />
                            {keyRemovalMessage && <p className="security-key-modal-status" role="status">{keyRemovalMessage}</p>}
                            {keyRemovalError && <p className="security-key-modal-error" role="alert">{keyRemovalError}</p>}
                            <div className="security-key-modal-actions">
                                <button type="button" className="security-key-modal-cancel" disabled={keyRemoving} onClick={closeKeyRemoval}>Cancel</button>
                                <button type="submit" className="security-key-modal-submit" disabled={keyRemoving}>
                                    {keyRemoving ? "Removing…" : "Remove security key"}
                                </button>
                            </div>
                        </form>
                    </section>
                </div>, document.body
            )}
        </main>
    );
}
