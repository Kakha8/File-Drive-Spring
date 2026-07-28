import { useNavigate } from "react-router-dom";
import { getCurrentUsername } from "../api/auth";
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
                </section>

                <section className="settings-card">
                    <h2>Appearance</h2>
                    <p>The application is currently using the dark theme.</p>
                </section>
            </section>
        </main>
    );
}