import DriveSidebar from "../components/DriveSidebar";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getAdminUsers } from "../api/users";
import { getAdminHealth, getAdminQuarantine, getAdminStorageUsage, getAdminTraffic } from "../api/admin";
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

const STORAGE_CATEGORIES = [
    ["files", "Files"],
    ["trash", "Trash"],
    ["quarantine", "Quarantine"],
    ["lockbox", "Lockbox"],
];

function StorageBreakdown({ storage }) {
    const usedBytes = Math.max(0, Number(storage?.usedBytes) || 0);
    return <>
        <div className="admin-storage-breakdown-bar" aria-label="Storage category breakdown">
            {STORAGE_CATEGORIES.map(([key, label]) => {
                const bytes = Math.max(0, Number(storage?.[key]?.bytes) || 0);
                return bytes > 0 && <span key={key} className={`category-${key}`} style={{ width: `${(bytes / Math.max(usedBytes, 1)) * 100}%` }} title={`${label}: ${formatBytes(bytes)}`} />;
            })}
        </div>
        <div className="admin-storage-breakdown-list">
            {STORAGE_CATEGORIES.map(([key, label]) => <span key={key}><i className={`category-${key}`} aria-hidden="true" /><b>{label}</b><small>{storage ? formatBytes(Number(storage[key]?.bytes) || 0) : "—"}</small></span>)}
        </div>
    </>;
}

function TrafficChart({ traffic }) {
    const points = Array.isArray(traffic?.points) ? traffic.points : [];
    const width = 420;
    const height = 112;
    const top = 8;
    const baseline = 96;
    const maxBytes = Math.max(1, ...points.map((point) => Number(point.totalBytes) || 0));
    const middleLabel = formatBytes(maxBytes / 2);
    const maximumLabel = formatBytes(maxBytes);
    const coordinates = points.map((point, index) => ({
        x: points.length <= 1 ? width / 2 : index * (width / (points.length - 1)),
        y: baseline - ((Number(point.totalBytes) || 0) / maxBytes) * (baseline - top),
    }));
    const line = coordinates.map((point, index) => `${index ? "L" : "M"}${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join(" ");
    const area = coordinates.length ? `${line} L${coordinates.at(-1).x.toFixed(1)} ${height} L0 ${height} Z` : "";

    return <>
        <div className="admin-traffic-mini-graph">
            <span className="admin-traffic-mini-y-axis" aria-hidden="true">
                <small>{maximumLabel}</small>
                <small>{middleLabel === maximumLabel ? "" : middleLabel}</small>
                <small>0 B</small>
            </span>
            <svg className="admin-traffic-chart" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" role="img" aria-label={`${formatBytes(Number(traffic?.totalBytes) || 0)} transferred during the last 24 hours; peak bucket ${maximumLabel}`}>
                <defs><linearGradient id="traffic-area" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#3b82f6" stopOpacity="0.28" /><stop offset="100%" stopColor="#3b82f6" stopOpacity="0" /></linearGradient></defs>
                <path className="admin-traffic-grid" d="M0 96H420M0 52H420M0 8H420" />
                {area && <path className="admin-traffic-area" d={area} />}
                {line && <path className="admin-traffic-line" d={line} />}
            </svg>
        </div>
        <span className="admin-traffic-axis"><small>24h ago</small><small>Now</small></span>
    </>;
}

function displayHealthStatus(status) {
    if (status === "CHECKING") return "Checking…";
    if (status === "HEALTHY") return "Healthy";
    if (status === "DEGRADED") return "Degraded";
    if (status === "CRITICAL") return "Critical";
    return "Unavailable";
}

function componentStatus(health, name) {
    return String(health?.checks?.[name]?.status || "UNKNOWN").toLowerCase();
}

function formatLatency(value) {
    const latency = Number(value);
    if (!Number.isFinite(latency)) return "—";
    if (latency < 1) return "<1 ms";
    return `${Math.round(latency).toLocaleString()} ms`;
}

function HealthCard({ health, onOpen }) {
    const loading = health === undefined;
    const status = loading ? "CHECKING" : health?.status || "UNKNOWN";
    const statusClass = status.toLowerCase();
    const ecsDetails = health?.checks?.ecs?.details;
    const p95 = health?.checks?.http?.details?.p95Ms;
    const activeUploads = Number(health?.checks?.uploadPipeline?.details?.active) || 0;
    const stuckUploads = Number(health?.checks?.uploadPipeline?.details?.stuck) || 0;
    const checkLabel = health
        ? `${health.passingChecks}/${health.totalChecks} checks passing`
        : loading ? "Running health checks…" : "Health data unavailable";

    return (
        <button type="button" className={`admin-chart-card admin-health-card status-${statusClass}`} aria-label={`Open system health details. Current status: ${displayHealthStatus(status)}`} onClick={onOpen}>
            <span className="admin-chart-heading"><strong><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M3 12h4l2.2-5 4.2 10 2.2-5H21" /><path d="M20 6.5A9 9 0 1 0 21 12" /></svg>System health</strong><span className="admin-health-live"><i aria-hidden="true" />Live</span></span>
            <div className="admin-health-overview">
                <div className="admin-health-ring" aria-hidden="true">
                    <svg viewBox="0 0 100 100"><circle cx="50" cy="50" r="40" /><circle className="admin-health-ring-value" cx="50" cy="50" r="40" pathLength="100" style={{ strokeDasharray: loading ? "22 78" : `${health ? (health.passingChecks / Math.max(health.totalChecks, 1)) * 100 : 0} 100` }} /></svg>
                    <strong>{displayHealthStatus(status)}</strong>
                </div>
                <div className="admin-health-summary">
                    <strong>{checkLabel}</strong>
                    <span><b>p95 latency</b>{formatLatency(p95)}</span>
                    <span><b>ECS tasks</b>{ecsDetails ? `API ${ecsDetails.backendRunningTasks ?? "—"}/${ecsDetails.backendDesiredTasks ?? "—"} · Web ${ecsDetails.frontendRunningTasks ?? "—"}/${ecsDetails.frontendDesiredTasks ?? "—"}` : "—"}</span>
                    {(activeUploads > 0 || stuckUploads > 0) && <span className={stuckUploads > 0 ? "admin-health-warning" : ""}><b>Uploads</b>{stuckUploads > 0 ? `${stuckUploads} stuck` : `${activeUploads} active`}</span>}
                </div>
            </div>
            <div className="admin-health-components">
                {[['api', 'API'], ['database', 'Database'], ['clamAv', 'ClamAV'], ['storage', 'Storage']].map(([key, label]) => (
                    <span key={key}><i className={`status-${componentStatus(health, key)}`} aria-hidden="true" />{label}</span>
                ))}
            </div>
            <small>{health?.checkedAt ? `Checked ${new Date(health.checkedAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" })}` : loading ? "Fetching latest health report" : "Unable to reach health checks"}</small>
        </button>
    );
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
    const [health, setHealth] = useState(undefined);
    const [traffic, setTraffic] = useState(null);
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

    useEffect(() => {
        let cancelled = false;
        let timer;

        const loadHealth = () => getAdminHealth()
            .then((result) => {
                if (!cancelled) setHealth(result);
            })
            .catch(() => {
                if (!cancelled) setHealth(null);
            });

        loadHealth();
        timer = window.setInterval(() => {
            if (!document.hidden) loadHealth();
        }, 30_000);

        const refreshWhenVisible = () => {
            if (!document.hidden) loadHealth();
        };
        document.addEventListener("visibilitychange", refreshWhenVisible);

        return () => {
            cancelled = true;
            window.clearInterval(timer);
            document.removeEventListener("visibilitychange", refreshWhenVisible);
        };
    }, []);

    useEffect(() => {
        let cancelled = false;
        let timer;

        const loadTraffic = () => getAdminTraffic()
            .then((result) => {
                if (!cancelled) setTraffic(result);
            })
            .catch(() => {
                if (!cancelled) setTraffic(null);
            });

        loadTraffic();
        timer = window.setInterval(() => {
            if (!document.hidden) loadTraffic();
        }, 5_000);

        const refreshWhenVisible = () => {
            if (!document.hidden) loadTraffic();
        };
        document.addEventListener("visibilitychange", refreshWhenVisible);

        return () => {
            cancelled = true;
            window.clearInterval(timer);
            document.removeEventListener("visibilitychange", refreshWhenVisible);
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
                            className="admin-chart-card admin-storage-dashboard-card"
                            onClick={() => navigate("/admin/storage")}
                        >
                            <span className="admin-chart-heading"><strong><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><ellipse cx="12" cy="5" rx="8" ry="3" /><path d="M4 5v6c0 1.7 3.6 3 8 3s8-1.3 8-3V5" /><path d="M4 11v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6" /></svg>Storage</strong><span aria-hidden="true">→</span></span>
                            <div className="admin-storage-overview">
                                <div className="admin-storage-gauge" role="img" aria-label={storage ? `${storage.usedPercentage.toFixed(1)} percent of storage used` : "Storage usage unavailable"}>
                                    <svg viewBox="0 0 180 180" aria-hidden="true">
                                        <circle className="admin-storage-gauge-track" pathLength="100" cx="90" cy="90" r="68" />
                                        {storagePercentage >= 0.05 && <circle className="admin-storage-gauge-value" pathLength="100" cx="90" cy="90" r="68" style={{ strokeDasharray: `${storagePercentage * 0.75} 100` }} />}
                                    </svg>
                                    <strong>{storage ? `${storage.usedPercentage.toFixed(1)}%` : "—"}</strong>
                                </div>
                                <div className="admin-storage-overview-copy">
                                    <span><strong>{storage ? formatBytes(storage.usedBytes) : "—"}</strong><small>of {storage ? formatBytes(storage.limitBytes) : "—"} used</small></span>
                                    <span><strong>{storage ? formatBytes(storage.availableBytes) : "—"}</strong><small>available</small></span>
                                </div>
                            </div>
                            <StorageBreakdown storage={storage} />
                            <span className="admin-storage-footer"><small>{storage ? `${Number(storage.objectCount).toLocaleString()} ${Number(storage.objectCount) === 1 ? "object" : "objects"}` : "Loading storage…"}</small><small>{storage && storage.usedPercentage >= 85 ? "Capacity warning" : "Across all storage areas"}</small></span>
                        </button>

                        <HealthCard health={health} onOpen={() => navigate("/admin/health")} />

                        <button type="button" className="admin-chart-card admin-traffic-card" onClick={() => navigate("/admin/traffic")}>
                            <span className="admin-chart-heading"><strong><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M3 17h3l3-7 4 10 3-7h5" /><path d="M18 6h3v3" /><path d="m21 6-5 5" /></svg>Traffic</strong><span aria-hidden="true">→</span></span>
                            <span className="admin-traffic-summary"><strong>{traffic ? formatBytes(Number(traffic.totalBytes) || 0) : "—"}</strong><small>{traffic ? `${formatBytes(Number(traffic.uploadedBytes) || 0)} up · ${formatBytes(Number(traffic.downloadedBytes) || 0)} down` : "Traffic unavailable"}</small></span>
                            <TrafficChart traffic={traffic} />
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
