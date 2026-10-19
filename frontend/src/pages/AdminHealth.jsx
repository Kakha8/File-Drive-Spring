import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getAdminHealth } from "../api/admin";
import DriveSidebar from "../components/DriveSidebar";
import NotificationMenu from "../components/NotificationMenu";
import UserMenu from "../components/UserMenu";

const CHECKS = [
    { key: "api", title: "API", description: "Application process and admin API" },
    { key: "http", title: "HTTP requests", description: "Errors and response time over the last five minutes" },
    { key: "ecs", title: "ECS service", description: "Running backend tasks versus desired tasks" },
    { key: "database", title: "Database", description: "Connection availability, pool usage, and latency" },
    { key: "clamAv", title: "ClamAV", description: "Scanner availability, version, and scan performance" },
    { key: "storage", title: "Object storage", description: "S3 bucket availability and request latency" },
    { key: "uploadPipeline", title: "Upload pipeline", description: "Active, scanning, failed, and stuck uploads" },
    { key: "system", title: "System resources", description: "Backend process CPU and JVM heap usage" },
];

function statusLabel(status) {
    if (status === "UP") return "Healthy";
    if (status === "DOWN") return "Down";
    if (status === "DEGRADED") return "Degraded";
    if (status === "HEALTHY") return "Healthy";
    if (status === "CRITICAL") return "Critical";
    return "Unknown";
}

function formatBytes(value) {
    if (!Number.isFinite(Number(value))) return "—";
    const units = ["B", "KiB", "MiB", "GiB", "TiB"];
    let amount = Number(value);
    let unit = 0;
    while (amount >= 1024 && unit < units.length - 1) { amount /= 1024; unit += 1; }
    return `${amount.toFixed(unit === 0 ? 0 : amount >= 10 ? 1 : 2)} ${units[unit]}`;
}

function formatNumber(value, suffix = "") {
    if (!Number.isFinite(Number(value))) return "—";
    const number = Number(value);
    return `${number < 10 && number % 1 ? number.toFixed(1) : Math.round(number).toLocaleString()}${suffix}`;
}

function metricRows(key, details = {}) {
    switch (key) {
        case "http":
            return [
                ["Requests", formatNumber(details.requestCount)],
                ["5xx errors", formatNumber(details.serverErrorCount)],
                ["5xx rate", formatNumber(Number(details.serverErrorRate) * 100, "%")],
                ["Average", formatNumber(details.averageMs, " ms")],
                ["p50", formatNumber(details.p50Ms, " ms")],
                ["p95", formatNumber(details.p95Ms, " ms")],
            ];
        case "ecs":
            return [
                ["Backend tasks", `${details.backendRunningTasks ?? "—"}/${details.backendDesiredTasks ?? "—"}`],
                ["Backend containers", Array.isArray(details.backendContainers) ? details.backendContainers.join(" + ") : "—"],
                ["Frontend tasks", `${details.frontendRunningTasks ?? "—"}/${details.frontendDesiredTasks ?? "—"}`],
                ["Frontend container", Array.isArray(details.frontendContainers) ? details.frontendContainers.join(" + ") : "—"],
            ];
        case "database":
            return [["Latency", formatNumber(details.latencyMs, " ms")], ["Connections", `${details.activeConnections ?? "—"}/${details.maxConnections ?? "—"}`]];
        case "clamAv":
            return [["Probe latency", formatNumber(details.latencyMs, " ms")], ["Scans", formatNumber(details.scanCount)], ["Failures", formatNumber(details.scanFailures)], ["Average scan", formatNumber(details.averageScanMs, " ms")], ["Version", details.version || "—"]];
        case "storage":
            return [["Latency", formatNumber(details.latencyMs, " ms")]];
        case "uploadPipeline": {
            const stages = details.stages || {};
            return [["Active", formatNumber(details.active)], ["Stuck", formatNumber(details.stuck)], ["Receiving", formatNumber(stages.RECEIVING)], ["Scanning", formatNumber(stages.SCANNING)], ["Failed", formatNumber(stages.FAILED)]];
        }
        case "system":
            return [["Process CPU", formatNumber(details.processCpuPercent, "%")], ["Heap used", formatBytes(details.heapUsedBytes)], ["Heap maximum", formatBytes(details.heapMaxBytes)]];
        default:
            return [];
    }
}

function healthExplanation(health) {
    if (!health) return "Waiting for the backend health report.";
    const failed = CHECKS.filter(({ key }) => health.checks?.[key]?.status === "DOWN").map(({ title }) => title);
    const degraded = CHECKS.filter(({ key }) => ["DEGRADED", "UNKNOWN"].includes(health.checks?.[key]?.status)).map(({ title }) => title);
    if (failed.length) return `${failed.join(", ")} ${failed.length === 1 ? "is" : "are"} reporting a failure.`;
    if (degraded.length) return `${degraded.join(", ")} ${degraded.length === 1 ? "needs" : "need"} attention.`;
    return "All monitored services are operating normally.";
}

function ComponentCard({ definition, check }) {
    const status = check?.status || "UNKNOWN";
    const details = check?.details || {};
    const rows = metricRows(definition.key, details);
    const message = details.error || details.reason || details.message;
    return <article className={`admin-health-component-card status-${status.toLowerCase()}`}>
        <div className="admin-health-component-heading">
            <span><i aria-hidden="true" /><strong>{definition.title}</strong></span>
            <b>{statusLabel(status)}</b>
        </div>
        <p>{definition.description}</p>
        {message && <div className="admin-health-component-message">{message}</div>}
        {!!rows.length && <dl>{rows.map(([label, value]) => <div key={label}><dt>{label}</dt><dd title={String(value)}>{value}</dd></div>)}</dl>}
    </article>;
}

export default function AdminHealth({ onLogout, sidebarOpen, onToggleSidebar }) {
    const navigate = useNavigate();
    const [health, setHealth] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [live, setLive] = useState(true);
    const [lastUpdated, setLastUpdated] = useState(null);

    const loadHealth = useCallback((showLoading = false) => {
        if (showLoading) setLoading(true);
        return getAdminHealth()
            .then((result) => { setHealth(result); setLastUpdated(new Date()); setError(""); })
            .catch((requestError) => setError(requestError.message || "Failed to load system health"))
            .finally(() => { if (showLoading) setLoading(false); });
    }, []);

    useEffect(() => {
        let timer;
        loadHealth(true);
        if (live) timer = window.setInterval(() => { if (!document.hidden) loadHealth(); }, 15_000);
        const refreshWhenVisible = () => { if (live && !document.hidden) loadHealth(); };
        document.addEventListener("visibilitychange", refreshWhenVisible);
        return () => { if (timer) window.clearInterval(timer); document.removeEventListener("visibilitychange", refreshWhenVisible); };
    }, [live, loadHealth]);

    const overall = health?.status || "UNKNOWN";
    const percentage = health ? Math.round((Number(health.passingChecks) / Math.max(Number(health.totalChecks), 1)) * 100) : 0;
    const failedCount = useMemo(() => Object.values(health?.checks || {}).filter((check) => check.status === "DOWN").length, [health]);
    const warningCount = useMemo(() => Object.values(health?.checks || {}).filter((check) => ["DEGRADED", "UNKNOWN"].includes(check.status)).length, [health]);

    return <main className="drive-page admin-users-page admin-logs-page admin-health-page">
        <DriveSidebar active="admin" sidebarOpen={sidebarOpen} onToggleSidebar={onToggleSidebar} onLogoutComplete={onLogout} />
        <section className="drive-main">
            <header className="drive-header admin-dashboard-header">
                <div className="admin-page-title"><button type="button" onClick={() => navigate("/admin")} aria-label="Back to dashboard">←</button><h1>System health</h1></div>
                <div className="drive-header-actions"><NotificationMenu onLogout={onLogout} /><UserMenu onLogout={onLogout} /></div>
            </header>
            <div className="admin-users-content admin-health-content">
                <div className="admin-health-page-topline">
                    <div><h2>Overall state</h2><p>Live application and infrastructure checks</p></div>
                    <div className="admin-health-page-controls">
                        <button type="button" className={`admin-live-toggle ${live ? "active" : ""}`} onClick={() => setLive((current) => !current)} aria-pressed={live}><i />{live ? "Live" : "Paused"}</button>
                        <span>{lastUpdated ? `Updated ${lastUpdated.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" })}` : "Updating…"}</span>
                        <button type="button" className="admin-health-refresh" onClick={() => loadHealth(true)} disabled={loading} aria-label="Refresh health checks"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M20 6v5h-5" /><path d="M4 18v-5h5" /><path d="M18.5 9A7 7 0 0 0 6 6.5L4 9M5.5 15A7 7 0 0 0 18 17.5l2-2.5" /></svg></button>
                    </div>
                </div>

                {error && <div className="admin-message error" role="alert">{error}</div>}
                <section className={`admin-health-overall-panel status-${overall.toLowerCase()}`}>
                    <div className="admin-health-page-ring" aria-hidden="true"><svg viewBox="0 0 100 100"><circle cx="50" cy="50" r="41" /><circle className="value" cx="50" cy="50" r="41" pathLength="100" style={{ strokeDasharray: `${percentage} 100` }} /></svg><strong>{percentage}%</strong></div>
                    <div className="admin-health-overall-copy"><span>Current status</span><h2>{statusLabel(overall)}</h2><p>{healthExplanation(health)}</p></div>
                    <div className="admin-health-overall-stats"><div><strong>{health?.passingChecks ?? "—"}/{health?.totalChecks ?? "—"}</strong><span>Passing checks</span></div><div><strong>{failedCount}</strong><span>Failures</span></div><div><strong>{warningCount}</strong><span>Warnings</span></div></div>
                </section>

                <div className="admin-health-section-heading"><div><h2>Component status</h2><p>Measurements are taken when this page refreshes</p></div><span>{health?.checkedAt ? `Backend checked ${new Date(health.checkedAt).toLocaleString()}` : "No report yet"}</span></div>
                <div className="admin-health-component-grid">
                    {CHECKS.map((definition) => <ComponentCard key={definition.key} definition={definition} check={health?.checks?.[definition.key]} />)}
                </div>
            </div>
        </section>
    </main>;
}
