import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getAdminTraffic } from "../api/admin";
import { getAdminUsers } from "../api/users";
import DriveSidebar from "../components/DriveSidebar";
import NotificationMenu from "../components/NotificationMenu";
import UserMenu from "../components/UserMenu";

function formatBytes(bytes) {
    if (!Number.isFinite(Number(bytes))) return "—";
    const units = ["B", "KiB", "MiB", "GiB", "TiB"];
    let value = Number(bytes);
    let unit = 0;
    while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit += 1; }
    return `${value.toFixed(unit === 0 ? 0 : value >= 10 ? 1 : 2)} ${units[unit]}`;
}

function formatTime(value, bucketSeconds) {
    if (!value) return "—";
    const options = bucketSeconds >= 86400
        ? { month: "short", day: "numeric" }
        : { month: "short", day: "numeric", hour: "numeric" };
    return new Intl.DateTimeFormat(undefined, options).format(new Date(value));
}

function formatFullTimestamp(value) {
    if (!value) return "—";
    return new Intl.DateTimeFormat(undefined, {
        dateStyle: "medium",
        timeStyle: "short",
    }).format(new Date(value));
}

function formatTrafficValue(bytes) {
    const numeric = Number(bytes) || 0;
    return `${formatBytes(numeric)} · ${numeric.toLocaleString()} bytes`;
}

function localDateTimeValue(date) {
    const offset = date.getTimezoneOffset() * 60_000;
    return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function MultiFilter({ label, options, selected, onToggle, onClear, searchable = false }) {
    const detailsRef = useRef(null);
    const [query, setQuery] = useState("");
    const visibleOptions = useMemo(() => {
        const normalized = query.trim().toLowerCase();
        return normalized ? options.filter((option) => option.label.toLowerCase().includes(normalized)) : options;
    }, [options, query]);

    useEffect(() => {
        function close(event) {
            if (detailsRef.current?.open && !detailsRef.current.contains(event.target)) detailsRef.current.removeAttribute("open");
        }
        document.addEventListener("pointerdown", close);
        return () => document.removeEventListener("pointerdown", close);
    }, []);

    return <details ref={detailsRef} className="admin-multi-filter">
        <summary><span>{label}</span><strong>{selected.length ? `${selected.length} selected` : "All"}</strong><svg className="admin-filter-chevron" viewBox="0 0 12 8" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m1 1.5 5 5 5-5" /></svg></summary>
        <div className="admin-multi-filter-menu">
            {searchable && <label className="admin-filter-search"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></svg><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={`Search ${label.toLowerCase()}`} /></label>}
            <div className="admin-filter-options">
                {visibleOptions.map((option) => <label key={option.value} className="admin-filter-option"><input type="checkbox" checked={selected.includes(option.value)} onChange={() => onToggle(option.value)} /><span>{option.label}</span></label>)}
                {!visibleOptions.length && <span className="admin-filter-empty">No matches</span>}
            </div>
            {!!selected.length && <button type="button" className="admin-filter-menu-clear" onClick={onClear}>Clear selection</button>}
        </div>
    </details>;
}

function TrafficGraph({ traffic }) {
    const [hoveredIndex, setHoveredIndex] = useState(null);
    const points = Array.isArray(traffic?.points) ? traffic.points : [];
    const width = 960;
    const height = 280;
    const plotLeft = 64;
    const top = 16;
    const baseline = 246;
    const maxBytes = Math.max(1, ...points.flatMap((point) => [Number(point.uploadedBytes) || 0, Number(point.downloadedBytes) || 0]));
    const coordinates = (key) => points.map((point, index) => ({
        x: points.length <= 1 ? plotLeft + (width - plotLeft) / 2 : plotLeft + index * ((width - plotLeft) / (points.length - 1)),
        y: baseline - ((Number(point[key]) || 0) / maxBytes) * (baseline - top),
        value: Number(point[key]) || 0,
        start: point.start,
    }));
    const uploadPoints = coordinates("uploadedBytes");
    const downloadPoints = coordinates("downloadedBytes");
    const path = (items) => items.map((point, index) => `${index ? "L" : "M"}${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join(" ");
    const hovered = hoveredIndex == null ? null : points[hoveredIndex];
    const hoveredX = hoveredIndex == null ? null : uploadPoints[hoveredIndex]?.x;
    const hoverBandWidth = points.length <= 1 ? width - plotLeft : (width - plotLeft) / (points.length - 1);
    const tooltipLeft = hoveredX == null ? 50 : (hoveredX / width) * 100;
    const tooltipAlignment = tooltipLeft < 22 ? "start" : tooltipLeft > 78 ? "end" : "center";
    const gridLines = [0, 1, 2, 3].map((index) => ({
        y: top + index * ((baseline - top) / 3),
        value: maxBytes * ((3 - index) / 3),
    }));

    return <div className="admin-traffic-graph-wrap" onPointerLeave={() => setHoveredIndex(null)}>
        <div className="admin-traffic-graph-stage">
            <svg className="admin-traffic-detail-graph" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" role="img" aria-label="Upload and download traffic over time">
                {gridLines.map((gridLine, index) => <line key={index} className="admin-traffic-detail-grid" x1={plotLeft} x2={width} y1={gridLine.y} y2={gridLine.y} />)}
                {!!uploadPoints.length && <path className="admin-traffic-upload-line" d={path(uploadPoints)} />}
                {!!downloadPoints.length && <path className="admin-traffic-download-line" d={path(downloadPoints)} />}
                {hoveredX != null && <line className="admin-traffic-hover-guide" x1={hoveredX} x2={hoveredX} y1={top} y2={baseline} />}
                {uploadPoints.map((point, index) => point.value > 0 && <circle key={`up-${index}`} className={`admin-traffic-upload-point ${hoveredIndex === index ? "highlighted" : ""}`} cx={point.x} cy={point.y} r={hoveredIndex === index ? 7 : 4} />)}
                {downloadPoints.map((point, index) => point.value > 0 && <circle key={`down-${index}`} className={`admin-traffic-download-point ${hoveredIndex === index ? "highlighted" : ""}`} cx={point.x} cy={point.y} r={hoveredIndex === index ? 7 : 4} />)}
                {points.map((point, index) => {
                    const x = Math.max(plotLeft, uploadPoints[index].x - hoverBandWidth / 2);
                    return <rect key={`hover-${point.start}`} className="admin-traffic-hover-target" x={x} y={0} width={Math.min(hoverBandWidth, width - x)} height={height} onPointerEnter={() => setHoveredIndex(index)} />;
                })}
            </svg>
            <div className="admin-traffic-y-axis" aria-hidden="true">
                {gridLines.map((gridLine, index) => <span key={index} style={{ top: `${(gridLine.y / height) * 100}%` }}>{formatBytes(gridLine.value)}</span>)}
            </div>
            {hovered && <div className={`admin-traffic-tooltip ${tooltipAlignment}`} style={{ left: `${tooltipLeft}%` }} role="status">
                <strong>{formatFullTimestamp(hovered.start)}</strong>
                <span className="upload"><i />Upload <b>{formatTrafficValue(hovered.uploadedBytes)}</b></span>
                <span className="download"><i />Download <b>{formatTrafficValue(hovered.downloadedBytes)}</b></span>
                <span className="total">Total <b>{formatTrafficValue(hovered.totalBytes)}</b></span>
            </div>}
        </div>
        <div className="admin-traffic-graph-axis"><span>{formatTime(points[0]?.start, traffic?.bucketSeconds)}</span><span>{formatTime(points.at(-1)?.start, traffic?.bucketSeconds)}</span></div>
    </div>;
}

export default function AdminTraffic({ onLogout, sidebarOpen, onToggleSidebar }) {
    const navigate = useNavigate();
    const [traffic, setTraffic] = useState(null);
    const [users, setUsers] = useState([]);
    const [selectedUsers, setSelectedUsers] = useState([]);
    const [directions, setDirections] = useState([]);
    const [types, setTypes] = useState([]);
    const [fromTime, setFromTime] = useState("");
    const [toTime, setToTime] = useState("");
    const [preset, setPreset] = useState("24h");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [live, setLive] = useState(true);
    const [lastUpdated, setLastUpdated] = useState(null);

    useEffect(() => {
        let cancelled = false;
        getAdminUsers().then((result) => { if (!cancelled) setUsers(Array.isArray(result) ? result : []); })
            .catch(() => { if (!cancelled) setError("Failed to load users"); });
        return () => { cancelled = true; };
    }, []);

    useEffect(() => {
        let cancelled = false;
        let timer;
        const presetHours = { "24h": 24, "7d": 168, "30d": 720 };

        const loadTraffic = (showLoading = false) => {
            if (showLoading) setLoading(true);
            const now = new Date();
            const rollingHours = live ? presetHours[preset] : null;
            const requestFrom = rollingHours
                ? new Date(now.getTime() - rollingHours * 60 * 60 * 1000).toISOString()
                : fromTime ? new Date(fromTime).toISOString() : "";
            const requestTo = rollingHours ? now.toISOString() : toTime ? new Date(toTime).toISOString() : "";
            return getAdminTraffic({ users: selectedUsers, directions, types, from: requestFrom, to: requestTo })
                .then((result) => {
                    if (!cancelled) {
                        setTraffic(result);
                        setLastUpdated(new Date());
                        setError("");
                    }
                })
                .catch((requestError) => { if (!cancelled) setError(requestError.message || "Failed to load traffic"); })
                .finally(() => { if (!cancelled && showLoading) setLoading(false); });
        };

        loadTraffic(true);
        if (live) timer = window.setInterval(() => {
            if (!document.hidden) loadTraffic(false);
        }, 2_000);
        const refreshWhenVisible = () => { if (live && !document.hidden) loadTraffic(false); };
        document.addEventListener("visibilitychange", refreshWhenVisible);
        return () => {
            cancelled = true;
            if (timer) window.clearInterval(timer);
            document.removeEventListener("visibilitychange", refreshWhenVisible);
        };
    }, [directions, fromTime, live, preset, selectedUsers, toTime, types]);

    const userOptions = useMemo(() => users.map((user) => ({ value: user.username, label: user.username })), [users]);
    const toggle = (setter, value) => setter((current) => current.includes(value) ? current.filter((item) => item !== value) : [...current, value]);
    const setRange = (hours, name) => {
        const end = new Date();
        const start = new Date(end.getTime() - hours * 60 * 60 * 1000);
        setFromTime(localDateTimeValue(start)); setToTime(localDateTimeValue(end)); setPreset(name);
    };
    const clearFilters = () => {
        setSelectedUsers([]); setDirections([]); setTypes([]); setFromTime(""); setToTime(""); setPreset("24h");
    };
    const filtersActive = selectedUsers.length || directions.length || types.length || fromTime || toTime;

    return <main className="drive-page admin-users-page admin-logs-page admin-traffic-page">
        <DriveSidebar active="admin" sidebarOpen={sidebarOpen} onToggleSidebar={onToggleSidebar} onLogoutComplete={onLogout} />
        <section className="drive-main">
            <header className="drive-header admin-dashboard-header">
                <div className="admin-page-title"><button type="button" onClick={() => navigate("/admin")} aria-label="Back to dashboard">←</button><h1>Traffic</h1></div>
                <div className="drive-header-actions"><NotificationMenu onLogout={onLogout} /><UserMenu onLogout={onLogout} /></div>
            </header>
            <div className="admin-users-content admin-traffic-content">
                <div className="admin-traffic-topline"><div><h2>File transfer traffic</h2><p>Application-level upload and download payloads</p></div><div className="admin-traffic-controls"><button type="button" className={`admin-live-toggle ${live ? "active" : ""}`} onClick={() => setLive((current) => !current)} aria-pressed={live}><i />{live ? "Live" : "Paused"}</button><span className="admin-traffic-updated">{lastUpdated ? `Updated ${lastUpdated.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" })}` : "Updating…"}</span><div className="admin-traffic-presets" aria-label="Traffic range"><button className={preset === "24h" ? "active" : ""} onClick={() => setRange(24, "24h")}>24h</button><button className={preset === "7d" ? "active" : ""} onClick={() => setRange(168, "7d")}>7d</button><button className={preset === "30d" ? "active" : ""} onClick={() => setRange(720, "30d")}>30d</button></div></div></div>

                <div className="admin-traffic-metrics">
                    <article><span>Total traffic</span><strong>{traffic ? formatBytes(traffic.totalBytes) : "—"}</strong><small>{traffic?.transferCount ?? 0} measured transfers</small></article>
                    <article><span className="admin-traffic-up-label">Uploaded</span><strong>{traffic ? formatBytes(traffic.uploadedBytes) : "—"}</strong><small>Files received by the application</small></article>
                    <article><span className="admin-traffic-down-label">Downloaded</span><strong>{traffic ? formatBytes(traffic.downloadedBytes) : "—"}</strong><small>Files sent by the application</small></article>
                    <article><span>Peak bucket</span><strong>{traffic ? formatBytes(traffic.peakBytes) : "—"}</strong><small>{traffic?.peakAt ? formatTime(traffic.peakAt, traffic.bucketSeconds) : "No measured traffic"}</small></article>
                </div>

                <section className="admin-traffic-chart-panel">
                    <div className="admin-traffic-chart-panel-heading"><div><h2>Traffic over time</h2><p>Bucket size adjusts to the selected period</p></div><div className="admin-traffic-legend"><span className="upload">Upload</span><span className="download">Download</span></div></div>
                    {loading ? <div className="admin-traffic-chart-state">Loading traffic…</div> : <TrafficGraph traffic={traffic} />}
                </section>

                <div className="admin-traffic-filter-bar">
                    <span className="admin-filter-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M4 5h16l-6 7v5l-4 2v-7Z" /></svg></span>
                    <MultiFilter label="Users" options={userOptions} selected={selectedUsers} onToggle={(value) => toggle(setSelectedUsers, value)} onClear={() => setSelectedUsers([])} searchable />
                    <MultiFilter label="Direction" options={[{ value: "UPLOAD", label: "Upload" }, { value: "DOWNLOAD", label: "Download" }]} selected={directions} onToggle={(value) => toggle(setDirections, value)} onClear={() => setDirections([])} />
                    <MultiFilter label="Transfer type" options={[{ value: "REGULAR", label: "Regular files" }, { value: "MALWARE", label: "Malware uploads" }, { value: "ZIP", label: "ZIP downloads" }]} selected={types} onToggle={(value) => toggle(setTypes, value)} onClear={() => setTypes([])} />
                    <label className="admin-time-filter"><span>From</span><input type="datetime-local" value={fromTime} max={toTime || undefined} onChange={(event) => { setFromTime(event.target.value); setPreset(""); }} /></label>
                    <label className="admin-time-filter"><span>To</span><input type="datetime-local" value={toTime} min={fromTime || undefined} onChange={(event) => { setToTime(event.target.value); setPreset(""); }} /></label>
                    {!!filtersActive && <button type="button" className="admin-clear-filters" onClick={clearFilters}>Clear filters</button>}
                </div>
                {error && <div className="admin-message error" role="alert">{error}</div>}
            </div>
        </section>
    </main>;
}
