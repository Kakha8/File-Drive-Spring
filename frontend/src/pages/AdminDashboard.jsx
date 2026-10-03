import DriveSidebar from "../components/DriveSidebar";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getAdminUsers } from "../api/users";
import { getAdminStorageUsage } from "../api/admin";

function formatBytes(bytes) {
    if (!Number.isFinite(bytes) || bytes < 0) return "—";
    const units = ["B", "KiB", "MiB", "GiB", "TiB"];
    let value = bytes;
    let unit = 0;
    while (value >= 1024 && unit < units.length - 1) {
        value /= 1024;
        unit += 1;
    }
    const digits = value >= 100 || unit === 0 ? 0 : value >= 10 ? 1 : 2;
    return `${value.toFixed(digits)} ${units[unit]}`;
}

export default function AdminDashboard({
    onLogout,
    sidebarOpen,
    onToggleSidebar,
}) {
    const navigate = useNavigate();
    const [userCount, setUserCount] = useState(null);
    const [storage, setStorage] = useState(null);
    const storagePercentage = Math.max(0, Math.min(100, storage?.usedPercentage ?? 0));

    useEffect(() => {
        let cancelled = false;

        getAdminUsers()
            .then((users) => {
                if (!cancelled) setUserCount(users.length);
            })
            .catch(() => {
                if (!cancelled) setUserCount(null);
            });

        getAdminStorageUsage()
            .then((usage) => {
                if (!cancelled) setStorage(usage);
            })
            .catch(() => {
                if (!cancelled) setStorage(null);
            });

        return () => {
            cancelled = true;
        };
    }, []);

    return (
        <main className="drive-page admin-dashboard-page">
            <DriveSidebar
                active="admin"
                sidebarOpen={sidebarOpen}
                onToggleSidebar={onToggleSidebar}
                onLogoutComplete={onLogout}
            />

            <section className="drive-main">
                <header className="drive-header admin-dashboard-header">
                    <div className="breadcrumbs">
                        <strong>Admin dashboard</strong>
                    </div>
                </header>

                <div className="admin-dashboard-content">
                    <div className="admin-dashboard-cards">
                        <button
                            type="button"
                            className="admin-summary-card"
                            onClick={() => navigate("/admin/users")}
                        >
                        <span className="admin-summary-card-icon" aria-hidden="true">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                                <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
                                <circle cx="9" cy="7" r="4" />
                                <path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
                            </svg>
                        </span>
                        <span className="admin-summary-card-copy">
                            <span>Users</span>
                            <strong>{userCount ?? "—"}</strong>
                            <small>Manage accounts and roles</small>
                        </span>
                        <span className="admin-summary-card-arrow" aria-hidden="true">→</span>
                        </button>

                        <section className="admin-storage-card" aria-label="Storage usage">
                            <span className="admin-storage-label">Storage</span>
                            <div className="admin-storage-gauge" role="img" aria-label={storage ? `${storage.usedPercentage.toFixed(1)} percent of storage used` : "Storage usage unavailable"}>
                                <svg viewBox="0 0 180 100" aria-hidden="true">
                                    <path className="admin-storage-gauge-track" pathLength="100" d="M 18 88 A 72 72 0 0 1 162 88" />
                                    {storagePercentage >= 0.05 && (
                                        <path
                                            className="admin-storage-gauge-value"
                                            pathLength="100"
                                            d="M 18 88 A 72 72 0 0 1 162 88"
                                            style={{ strokeDasharray: `${storagePercentage} 100` }}
                                        />
                                    )}
                                </svg>
                                <strong>{storage ? `${storage.usedPercentage.toFixed(1)}%` : "—"}</strong>
                            </div>
                            <small>{storage ? `${formatBytes(storage.usedBytes)} of ${formatBytes(storage.limitBytes)} used` : "Storage usage unavailable"}</small>
                        </section>
                    </div>

                    <div className="admin-dashboard-placeholder">
                        <h2>More dashboard tools are coming soon</h2>
                        <p>Quarantine, audit activity, and system health will appear here.</p>
                    </div>
                </div>
            </section>
        </main>
    );
}
