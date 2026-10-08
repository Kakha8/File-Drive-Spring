import { Fragment, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getAdminQuarantine, getAdminQuarantineSignatures, getAdminQuarantineTimeline } from "../api/admin";
import { getAdminUsers } from "../api/users";
import DriveSidebar from "../components/DriveSidebar";
import NotificationMenu from "../components/NotificationMenu";
import UserMenu from "../components/UserMenu";

function formatTimestamp(value) {
    if (!value) return "—";
    return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function formatBytes(bytes) {
    if (!Number.isFinite(Number(bytes))) return "—";
    const units = ["B", "KiB", "MiB", "GiB", "TiB"];
    let value = Number(bytes);
    let unit = 0;
    while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit += 1; }
    return `${value.toFixed(unit === 0 ? 0 : value >= 10 ? 1 : 2)} ${units[unit]}`;
}

function signatureName(response) {
    if (!response) return "Unknown signature";
    const match = response.match(/:\s*(.+?)\s+FOUND$/i);
    return match?.[1] || response.replace(/\s+FOUND$/i, "");
}

function QuarantineTimeline({ timeline }) {
    const points = Array.isArray(timeline?.points) ? timeline.points : [];
    const width = 760;
    const height = 190;
    const paddingX = 18;
    const top = 18;
    const baseline = 158;
    const maxCount = Math.max(1, ...points.map((point) => Number(point.count) || 0));
    const coordinates = points.map((point, index) => ({
        x: points.length <= 1 ? width / 2 : paddingX + index * ((width - paddingX * 2) / (points.length - 1)),
        y: baseline - ((Number(point.count) || 0) / maxCount) * (baseline - top),
        count: Number(point.count) || 0,
        start: point.start,
    }));
    const line = coordinates.map((point, index) => `${index === 0 ? "M" : "L"}${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join(" ");
    const area = coordinates.length ? `${line} L${coordinates.at(-1).x.toFixed(1)} ${baseline} L${coordinates[0].x.toFixed(1)} ${baseline} Z` : "";
    const formatRange = (value) => value ? new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" }).format(new Date(value)) : "—";

    return <section className="admin-quarantine-timeline" aria-labelledby="quarantine-timeline-title">
        <div className="admin-quarantine-chart-heading">
            <div><span className="admin-quarantine-chart-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/><path d="m9 12 2 2 4-4"/></svg></span><div><h2 id="quarantine-timeline-title">Detections over time</h2><p>Files isolated by ClamAV</p></div></div>
            <strong>{Number(timeline?.totalDetections) || 0}<small> detections</small></strong>
        </div>
        <div className="admin-quarantine-chart-wrap">
            <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" role="img" aria-label="Quarantine detections over time">
                {[0, 1, 2, 3].map((lineIndex) => <line key={lineIndex} className="admin-quarantine-chart-grid" x1="0" x2={width} y1={top + lineIndex * ((baseline - top) / 3)} y2={top + lineIndex * ((baseline - top) / 3)} />)}
                {area && <path className="admin-quarantine-chart-area" d={area} />}
                {line && <path className="admin-quarantine-chart-line" d={line} />}
                {coordinates.filter((point) => point.count > 0).map((point, index) => <circle key={index} className="admin-quarantine-chart-point" cx={point.x} cy={point.y} r="4"><title>{point.count} detection{point.count === 1 ? "" : "s"} · {formatRange(point.start)}</title></circle>)}
            </svg>
            <div className="admin-quarantine-chart-axis"><span>{formatRange(points[0]?.start)}</span><span>{formatRange(points.at(-1)?.start)}</span></div>
        </div>
    </section>;
}

function MultiFilter({ label, options, selected, onToggle, onClear }) {
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

    return (
        <details ref={detailsRef} className="admin-multi-filter">
            <summary><span>{label}</span><strong>{selected.length ? `${selected.length} selected` : "All"}</strong><svg className="admin-filter-chevron" viewBox="0 0 12 8" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m1 1.5 5 5 5-5" /></svg></summary>
            <div className="admin-multi-filter-menu">
                <label className="admin-filter-search"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></svg><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={`Search ${label.toLowerCase()}`} /></label>
                <div className="admin-filter-options">
                    {visibleOptions.map((option) => <label key={option.value} className="admin-filter-option"><input type="checkbox" checked={selected.includes(option.value)} onChange={() => onToggle(option.value)} /><span>{option.label}</span></label>)}
                    {visibleOptions.length === 0 && <span className="admin-filter-empty">No matches</span>}
                </div>
                {selected.length > 0 && <button type="button" className="admin-filter-menu-clear" onClick={onClear}>Clear selection</button>}
            </div>
        </details>
    );
}

export default function AdminQuarantine({ onLogout, sidebarOpen, onToggleSidebar }) {
    const navigate = useNavigate();
    const [files, setFiles] = useState([]);
    const [users, setUsers] = useState([]);
    const [signatures, setSignatures] = useState([]);
    const [selectedUsers, setSelectedUsers] = useState([]);
    const [selectedSignatures, setSelectedSignatures] = useState([]);
    const [query, setQuery] = useState("");
    const [fromTime, setFromTime] = useState("");
    const [toTime, setToTime] = useState("");
    const [pageSize, setPageSize] = useState("10");
    const [page, setPage] = useState(1);
    const [totalElements, setTotalElements] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [expanded, setExpanded] = useState(() => new Set());
    const [timeline, setTimeline] = useState({ points: [], totalDetections: 0 });

    useEffect(() => {
        let cancelled = false;
        Promise.all([getAdminUsers(), getAdminQuarantineSignatures()])
            .then(([loadedUsers, loadedSignatures]) => {
                if (cancelled) return;
                setUsers(Array.isArray(loadedUsers) ? loadedUsers : []);
                setSignatures(Array.isArray(loadedSignatures) ? loadedSignatures : []);
            })
            .catch((requestError) => { if (!cancelled) setError(requestError.message || "Failed to load filters"); });
        return () => { cancelled = true; };
    }, []);

    useEffect(() => {
        let cancelled = false;
        getAdminQuarantine({
            page: page - 1,
            size: pageSize === "all" ? 50 : Number(pageSize),
            all: pageSize === "all",
            query,
            users: selectedUsers,
            signatures: selectedSignatures,
            from: fromTime ? new Date(fromTime).toISOString() : "",
            to: toTime ? new Date(toTime).toISOString() : "",
        }).then((data) => {
            if (cancelled) return;
            setError("");
            setFiles(Array.isArray(data.files) ? data.files : []);
            setTotalElements(Number(data.totalElements) || 0);
            setTotalPages(Math.max(Number(data.totalPages) || 1, 1));
        }).catch((requestError) => { if (!cancelled) setError(requestError.message || "Failed to load quarantined files"); })
            .finally(() => { if (!cancelled) setLoading(false); });
        return () => { cancelled = true; };
    }, [fromTime, page, pageSize, query, selectedSignatures, selectedUsers, toTime]);

    useEffect(() => {
        let cancelled = false;
        getAdminQuarantineTimeline({
            query,
            users: selectedUsers,
            signatures: selectedSignatures,
            from: fromTime ? new Date(fromTime).toISOString() : "",
            to: toTime ? new Date(toTime).toISOString() : "",
        }).then((data) => { if (!cancelled) setTimeline(data); })
            .catch((requestError) => { if (!cancelled) setError(requestError.message || "Failed to load quarantine timeline"); });
        return () => { cancelled = true; };
    }, [fromTime, query, selectedSignatures, selectedUsers, toTime]);

    const userOptions = useMemo(() => users.map((user) => ({ value: user.username, label: user.username })), [users]);
    const signatureOptions = useMemo(() => signatures.map((signature) => ({ value: signature, label: signatureName(signature) })), [signatures]);
    const currentPage = pageSize === "all" ? 1 : Math.min(page, totalPages);
    const firstVisible = totalElements === 0 ? 0 : (currentPage - 1) * Number(pageSize === "all" ? totalElements : pageSize) + 1;
    const lastVisible = pageSize === "all" ? totalElements : Math.min(currentPage * Number(pageSize), totalElements);

    function toggleSelection(setter, value) {
        setter((current) => current.includes(value) ? current.filter((item) => item !== value) : [...current, value]);
        setPage(1);
    }

    function toggleExpanded(id) {
        setExpanded((current) => {
            const next = new Set(current);
            if (next.has(id)) next.delete(id); else next.add(id);
            return next;
        });
    }

    function clearFilters() {
        setQuery(""); setSelectedUsers([]); setSelectedSignatures([]); setFromTime(""); setToTime(""); setPage(1);
    }

    return (
        <main className="drive-page admin-users-page admin-logs-page admin-quarantine-page">
            <DriveSidebar active="admin" sidebarOpen={sidebarOpen} onToggleSidebar={onToggleSidebar} onLogoutComplete={onLogout} />
            <section className="drive-main">
                <header className="drive-header admin-dashboard-header">
                    <div className="admin-page-title"><button type="button" onClick={() => navigate("/admin")} aria-label="Back to dashboard">←</button><h1>Quarantine</h1></div>
                    <div className="drive-header-actions"><NotificationMenu onLogout={onLogout} /><UserMenu onLogout={onLogout} /></div>
                </header>

                <div className="admin-users-content">
                    <QuarantineTimeline timeline={timeline} />
                    <div className="admin-logs-sticky-controls">
                        <div className="admin-users-toolbar">
                            <div><h2>Quarantined files</h2><p>Review files isolated by ClamAV</p></div>
                            <span>{totalElements} {totalElements === 1 ? "file" : "files"}</span>
                        </div>
                        <div className="admin-log-filters admin-quarantine-filters" aria-label="Quarantine filters">
                            <label className="admin-search admin-quarantine-search"><span aria-hidden="true">⌕</span><input type="search" value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }} placeholder="Search filenames" aria-label="Search filenames" /></label>
                            <MultiFilter label="Users" options={userOptions} selected={selectedUsers} onToggle={(value) => toggleSelection(setSelectedUsers, value)} onClear={() => { setSelectedUsers([]); setPage(1); }} />
                            <MultiFilter label="Signatures" options={signatureOptions} selected={selectedSignatures} onToggle={(value) => toggleSelection(setSelectedSignatures, value)} onClear={() => { setSelectedSignatures([]); setPage(1); }} />
                            <label className="admin-time-filter"><span>From</span><input type="datetime-local" value={fromTime} max={toTime || undefined} onChange={(event) => { setFromTime(event.target.value); setPage(1); }} /></label>
                            <label className="admin-time-filter"><span>To</span><input type="datetime-local" value={toTime} min={fromTime || undefined} onChange={(event) => { setToTime(event.target.value); setPage(1); }} /></label>
                            {(query || selectedUsers.length || selectedSignatures.length || fromTime || toTime) && <button type="button" className="admin-clear-filters" onClick={clearFilters}>Clear filters</button>}
                        </div>
                    </div>

                    {error && <div className="admin-message error" role="alert">{error}</div>}
                    <div className="admin-users-table-wrap">
                        <table className="admin-users-table admin-quarantine-table">
                            <thead><tr><th>Detected</th><th>File</th><th>User</th><th>Signature</th><th>Size</th><th aria-label="Details" /></tr></thead>
                            <tbody>
                                {files.map((file) => {
                                    const isExpanded = expanded.has(file.id);
                                    return <Fragment key={file.id}>
                                        <tr>
                                            <td className="admin-log-time">{formatTimestamp(file.createdAt)}</td>
                                            <td className="admin-quarantine-name" title={file.originalFilename}><strong>{file.originalFilename}</strong><small>{file.contentType || "Unknown type"}</small></td>
                                            <td><strong>{file.username || "—"}</strong></td>
                                            <td><span className="admin-threat-badge" title={file.clamAvResponse}>{signatureName(file.clamAvResponse)}</span></td>
                                            <td className="admin-log-time">{formatBytes(file.size)}</td>
                                            <td className="admin-quarantine-expand-cell"><button type="button" className={`admin-log-expand ${isExpanded ? "expanded" : ""}`} onClick={() => toggleExpanded(file.id)} aria-expanded={isExpanded} aria-label={isExpanded ? "Hide quarantine details" : "Show quarantine details"}><svg viewBox="0 0 12 8" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m1 1.5 5 5 5-5" /></svg></button></td>
                                        </tr>
                                        {isExpanded && <tr className="admin-log-expanded-row"><td colSpan="6"><dl className="admin-log-detail-grid">
                                            <div><dt>Original filename</dt><dd>{file.originalFilename}</dd></div><div><dt>Object key</dt><dd>{file.objectKey}</dd></div><div><dt>Checksum</dt><dd>{file.checksum}</dd></div><div><dt>ClamAV response</dt><dd>{file.clamAvResponse}</dd></div><div><dt>Content type</dt><dd>{file.contentType || "—"}</dd></div><div><dt>Parent folder ID</dt><dd>{file.parentFolderId ?? "—"}</dd></div><div><dt>User ID</dt><dd>{file.userId ?? "—"}</dd></div><div><dt>Quarantine ID</dt><dd>{file.id}</dd></div>
                                        </dl></td></tr>}
                                    </Fragment>;
                                })}
                            </tbody>
                        </table>
                        {loading && <div className="admin-table-state">Loading quarantined files…</div>}
                        {!loading && !error && files.length === 0 && <div className="admin-table-state">No quarantined files match these filters.</div>}
                    </div>

                    {!loading && !error && totalElements > 0 && <div className="admin-pagination"><span>Showing {firstVisible}–{lastVisible} of {totalElements}</span><div className="admin-pagination-controls"><label>Rows per page<select value={pageSize} onChange={(event) => { setPageSize(event.target.value); setPage(1); }}><option value="10">10</option><option value="25">25</option><option value="50">50</option><option value="all">All</option></select></label><button type="button" onClick={() => setPage((value) => Math.max(1, value - 1))} disabled={currentPage === 1}>←</button><span>{pageSize === "all" ? "1 / 1" : `${currentPage} / ${totalPages}`}</span><button type="button" onClick={() => setPage((value) => Math.min(totalPages, value + 1))} disabled={currentPage === totalPages}>→</button></div></div>}
                </div>
            </section>
        </main>
    );
}
