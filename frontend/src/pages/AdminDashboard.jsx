import DriveSidebar from "../components/DriveSidebar";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getAdminUsers } from "../api/users";
import { getAdminQuarantine, getAdminStorageUsage } from "../api/admin";
import NotificationMenu from "../components/NotificationMenu";
import UserMenu from "../components/UserMenu";

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

function formatCompactCount(value) {
    if (!Number.isFinite(value) || value < 0) return "—";
    return new Intl.NumberFormat(undefined, {
        notation: "compact",
        maximumFractionDigits: 1,
    }).format(value);
}

export default function AdminDashboard({
    onLogout,
    sidebarOpen,
    onToggleSidebar,
}) {
    const navigate = useNavigate();
    const [userCount, setUserCount] = useState(null);
    const [quarantineCount, setQuarantineCount] = useState(null);
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

        getAdminQuarantine({ page: 0, size: 1 })
            .then((result) => {
                if (!cancelled) setQuarantineCount(Number(result.totalElements) || 0);
            })
            .catch(() => {
                if (!cancelled) setQuarantineCount(null);
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
                    <div className="drive-header-actions">
                        <NotificationMenu onLogout={onLogout} />
                        <UserMenu onLogout={onLogout} />
                    </div>
                </header>

                <div className="admin-dashboard-content">
                    <div className="admin-dashboard-charts">
                        <button
                            type="button"
                            className="admin-chart-card"
                            onClick={() => navigate("/admin/storage")}
                        >
                            <span className="admin-chart-heading"><strong><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><ellipse cx="12" cy="5" rx="8" ry="3" /><path d="M4 5v6c0 1.7 3.6 3 8 3s8-1.3 8-3V5" /><path d="M4 11v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6" /></svg>Storage</strong><span aria-hidden="true">→</span></span>
                            <div className="admin-storage-gauge" role="img" aria-label={storage ? `${storage.usedPercentage.toFixed(1)} percent of storage used` : "Storage usage unavailable"}>
                                <svg viewBox="0 0 180 180" aria-hidden="true">
                                    <circle className="admin-storage-gauge-track" pathLength="100" cx="90" cy="90" r="68" />
                                    {storagePercentage >= 0.05 && (
                                        <circle
                                            className="admin-storage-gauge-value"
                                            pathLength="100"
                                            cx="90"
                                            cy="90"
                                            r="68"
                                            style={{ strokeDasharray: `${storagePercentage * 0.75} 100` }}
                                        />
                                    )}
                                </svg>
                                <strong>{storage ? `${storage.usedPercentage.toFixed(1)}%` : "—"}</strong>
                            </div>
                            <small>{storage ? `${formatBytes(storage.usedBytes)} of ${formatBytes(storage.limitBytes)} used` : "Storage usage unavailable"}</small>
                        </button>

                        <button type="button" className="admin-chart-card admin-traffic-card" onClick={() => navigate("/admin/traffic")}>
                            <span className="admin-chart-heading"><strong><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M3 17h3l3-7 4 10 3-7h5" /><path d="M18 6h3v3" /><path d="m21 6-5 5" /></svg>Traffic</strong><span aria-hidden="true">→</span></span>
                            <span className="admin-traffic-summary"><strong>1.8 GB</strong><small>Mock traffic · last 24 hours</small></span>
                            <svg className="admin-traffic-chart" viewBox="0 0 420 112" preserveAspectRatio="none" role="img" aria-label="Mock traffic chart for the last 24 hours">
                                <defs>
                                    <linearGradient id="traffic-area" x1="0" y1="0" x2="0" y2="1">
                                        <stop offset="0%" stopColor="#3b82f6" stopOpacity="0.28" />
                                        <stop offset="100%" stopColor="#3b82f6" stopOpacity="0" />
                                    </linearGradient>
                                </defs>
                                <path className="admin-traffic-grid" d="M0 88H420M0 52H420M0 16H420" />
                                <path className="admin-traffic-area" d="M0 90 C28 86 38 64 67 70 S110 87 137 58 S180 42 205 61 S250 80 274 43 S317 19 344 39 S386 54 420 18 L420 112 L0 112 Z" />
                                <path className="admin-traffic-line" d="M0 90 C28 86 38 64 67 70 S110 87 137 58 S180 42 205 61 S250 80 274 43 S317 19 344 39 S386 54 420 18" />
                            </svg>
                            <span className="admin-traffic-axis"><small>24h ago</small><small>Now</small></span>
                        </button>
                    </div>

                    <div className="admin-dashboard-shortcuts">
                        <button type="button" className="admin-summary-card compact" onClick={() => navigate("/admin/users")}>
                            <span className="admin-summary-card-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" /></svg></span>
                            <span className="admin-summary-card-copy"><span>Users</span><strong>{userCount ?? "—"}</strong><small>Manage accounts and roles</small></span><span className="admin-summary-card-arrow" aria-hidden="true">→</span>
                        </button>
                        <button type="button" className="admin-summary-card compact" onClick={() => navigate("/admin/quarantine")}>
                            <span className="admin-summary-card-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M12 3 3.5 7v5c0 5 3.6 8 8.5 9 4.9-1 8.5-4 8.5-9V7L12 3Z" /><path d="M9 9l6 6M15 9l-6 6" /></svg></span>
                            <span className="admin-summary-card-copy"><span>Quarantine</span><strong title={quarantineCount == null ? undefined : `${quarantineCount.toLocaleString()} quarantined files`} aria-label={quarantineCount == null ? "Quarantine count unavailable" : `${quarantineCount.toLocaleString()} quarantined files`}>{formatCompactCount(quarantineCount)}</strong><small>Review isolated files</small></span><span className="admin-summary-card-arrow" aria-hidden="true">→</span>
                        </button>
                        <button type="button" className="admin-summary-card compact" onClick={() => navigate("/admin/logs")}>
                            <span className="admin-summary-card-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M6 3h9l3 3v15H6z" /><path d="M9 10h6M9 14h6M9 18h4" /></svg></span>
                            <span className="admin-summary-card-copy admin-summary-card-copy-no-value"><span>Logs</span><small>Inspect system activity</small></span><span className="admin-summary-card-arrow" aria-hidden="true">→</span>
                        </button>
                    </div>
                </div>
            </section>
        </main>
    );
}
