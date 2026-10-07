import { Fragment, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getAdminLogActions, getAdminLogs } from "../api/admin";
import { getAdminUsers } from "../api/users";
import DriveSidebar from "../components/DriveSidebar";
import NotificationMenu from "../components/NotificationMenu";
import UserMenu from "../components/UserMenu";

function humanize(value) {
    if (!value) return "—";
    const text = value.toLowerCase().replaceAll("_", " ");
    return text.charAt(0).toUpperCase() + text.slice(1);
}

function formatTimestamp(value) {
    if (!value) return "—";
    return new Intl.DateTimeFormat(undefined, {
        dateStyle: "medium",
        timeStyle: "short",
    }).format(new Date(value));
}

function summarizeDetails(details) {
    if (!details) return "—";
    try {
        const parsed = JSON.parse(details);
        const usefulValue = parsed.name || parsed.resourceName || parsed.fileName || parsed.folderName || parsed.newName;
        return usefulValue || Object.entries(parsed)
            .slice(0, 2)
            .map(([key, value]) => `${humanize(key)}: ${String(value)}`)
            .join(" · ") || "—";
    } catch {
        return details;
    }
}

function parseExportDetails(details) {
    if (!details) return null;
    try {
        return JSON.parse(details);
    } catch {
        return { raw: details };
    }
}

function detailEntries(details) {
    if (!details) return [];
    try {
        const parsed = JSON.parse(details);
        return parsed && typeof parsed === "object" && !Array.isArray(parsed)
            ? Object.entries(parsed)
            : [];
    } catch {
        return [];
    }
}

function hasAdditionalDetails(details) {
    const summaryKeys = new Set(["name", "resourceName", "fileName", "folderName", "newName"]);
    return detailEntries(details).some(([key]) => !summaryKeys.has(key));
}

function formatDetailLabel(value) {
    const text = value.replace(/([a-z])([A-Z])/g, "$1 $2").replaceAll("_", " ").toLowerCase();
    return text.charAt(0).toUpperCase() + text.slice(1);
}

function formatDetailValue(value) {
    if (value === null || value === undefined || value === "") return "—";
    return typeof value === "object" ? JSON.stringify(value) : String(value);
}

function MultiFilter({ label, options, selected, onToggle, onClear, searchable = false }) {
    const detailsRef = useRef(null);
    const [query, setQuery] = useState("");
    const visibleOptions = useMemo(() => {
        const normalizedQuery = query.trim().toLowerCase();
        if (!normalizedQuery) return options;
        return options.filter((option) => option.label.toLowerCase().includes(normalizedQuery));
    }, [options, query]);

    useEffect(() => {
        function closeOnOutsideClick(event) {
            if (detailsRef.current?.open && !detailsRef.current.contains(event.target)) {
                detailsRef.current.removeAttribute("open");
            }
        }

        document.addEventListener("pointerdown", closeOnOutsideClick);
        return () => document.removeEventListener("pointerdown", closeOnOutsideClick);
    }, []);

    return (
        <details ref={detailsRef} className="admin-multi-filter">
            <summary>
                <span>{label}</span>
                <strong>{selected.length === 0 ? "All" : `${selected.length} selected`}</strong>
                <svg className="admin-filter-chevron" viewBox="0 0 12 8" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="m1 1.5 5 5 5-5" />
                </svg>
            </summary>
            <div className="admin-multi-filter-menu">
                {searchable && (
                    <label className="admin-filter-search">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></svg>
                        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={`Search ${label.toLowerCase()}`} />
                    </label>
                )}
                <div className="admin-filter-options">
                    {visibleOptions.map((option) => (
                        <label key={option.value} className="admin-filter-option">
                            <input
                                type="checkbox"
                                checked={selected.includes(option.value)}
                                onChange={() => onToggle(option.value)}
                            />
                            <span>{option.label}</span>
                        </label>
                    ))}
                    {visibleOptions.length === 0 && <span className="admin-filter-empty">No matches</span>}
                </div>
                {selected.length > 0 && <button type="button" className="admin-filter-menu-clear" onClick={onClear}>Clear selection</button>}
            </div>
        </details>
    );
}

export default function AdminLogs({ onLogout, sidebarOpen, onToggleSidebar }) {
    const navigate = useNavigate();
    const [logs, setLogs] = useState([]);
    const [pageSize, setPageSize] = useState("10");
    const [page, setPage] = useState(1);
    const [totalElements, setTotalElements] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [exporting, setExporting] = useState(false);
    const [users, setUsers] = useState([]);
    const [actions, setActions] = useState([]);
    const [selectedUsers, setSelectedUsers] = useState([]);
    const [selectedActions, setSelectedActions] = useState([]);
    const [fromTime, setFromTime] = useState("");
    const [toTime, setToTime] = useState("");
    const [expandedLogs, setExpandedLogs] = useState(() => new Set());

    useEffect(() => {
        let cancelled = false;
        Promise.all([getAdminUsers(), getAdminLogActions()])
            .then(([loadedUsers, loadedActions]) => {
                if (cancelled) return;
                setUsers(Array.isArray(loadedUsers) ? loadedUsers : []);
                setActions(Array.isArray(loadedActions) ? loadedActions : []);
            })
            .catch((requestError) => {
                if (!cancelled) setError(requestError.message || "Failed to load filters");
            });
        return () => {
            cancelled = true;
        };
    }, []);

    useEffect(() => {
        let cancelled = false;

        getAdminLogs({
            page: page - 1,
            size: pageSize === "all" ? 50 : Number(pageSize),
            all: pageSize === "all",
            users: selectedUsers,
            actions: selectedActions,
            from: fromTime ? new Date(fromTime).toISOString() : "",
            to: toTime ? new Date(toTime).toISOString() : "",
        })
            .then((data) => {
                if (cancelled) return;
                setError("");
                setLogs(Array.isArray(data.logs) ? data.logs : []);
                setTotalElements(Number(data.totalElements) || 0);
                setTotalPages(Math.max(Number(data.totalPages) || 1, 1));
            })
            .catch((requestError) => {
                if (!cancelled) setError(requestError.message || "Failed to load logs");
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });

        return () => {
            cancelled = true;
        };
    }, [fromTime, page, pageSize, selectedActions, selectedUsers, toTime]);

    const userOptions = useMemo(
        () => users.map((user) => ({ value: user.username, label: user.username })),
        [users]
    );
    const actionOptions = useMemo(
        () => actions.map((action) => ({ value: action, label: humanize(action) })),
        [actions]
    );

    function toggleSelection(setter, value) {
        setter((current) => current.includes(value)
            ? current.filter((item) => item !== value)
            : [...current, value]);
        setPage(1);
    }

    function toggleLogDetails(logId) {
        setExpandedLogs((current) => {
            const next = new Set(current);
            if (next.has(logId)) next.delete(logId);
            else next.add(logId);
            return next;
        });
    }

    async function exportJson() {
        setExporting(true);
        setError("");
        try {
            const data = await getAdminLogs({
                all: true,
                users: selectedUsers,
                actions: selectedActions,
                from: fromTime ? new Date(fromTime).toISOString() : "",
                to: toTime ? new Date(toTime).toISOString() : "",
            });
            const exportData = {
                exportedAt: new Date().toISOString(),
                filters: {
                    users: selectedUsers,
                    actions: selectedActions,
                    from: fromTime ? new Date(fromTime).toISOString() : null,
                    to: toTime ? new Date(toTime).toISOString() : null,
                },
                totalElements: Number(data.totalElements) || 0,
                logs: Array.isArray(data.logs)
                    ? data.logs.map((log) => ({
                        ...log,
                        details: parseExportDetails(log.details),
                    }))
                    : [],
            };
            const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: "application/json" });
            const url = URL.createObjectURL(blob);
            const anchor = document.createElement("a");
            anchor.href = url;
            anchor.download = `file-drive-logs-${new Date().toISOString().slice(0, 10)}.json`;
            document.body.appendChild(anchor);
            anchor.click();
            anchor.remove();
            URL.revokeObjectURL(url);
        } catch (requestError) {
            setError(requestError.message || "Failed to export logs");
        } finally {
            setExporting(false);
        }
    }

    const currentPage = pageSize === "all" ? 1 : Math.min(page, totalPages);
    const firstVisible = totalElements === 0 ? 0 : (currentPage - 1) * Number(pageSize === "all" ? totalElements : pageSize) + 1;
    const lastVisible = pageSize === "all"
        ? totalElements
        : Math.min(currentPage * Number(pageSize), totalElements);

    return (
        <main className="drive-page admin-users-page admin-logs-page">
            <DriveSidebar
                active="admin"
                sidebarOpen={sidebarOpen}
                onToggleSidebar={onToggleSidebar}
                onLogoutComplete={onLogout}
            />
            <section className="drive-main">
                <header className="drive-header admin-dashboard-header">
                    <div className="admin-page-title">
                        <button type="button" onClick={() => navigate("/admin")} aria-label="Back to dashboard">←</button>
                        <h1>Logs</h1>
                    </div>
                    <div className="drive-header-actions">
                        <NotificationMenu onLogout={onLogout} />
                        <UserMenu onLogout={onLogout} />
                    </div>
                </header>

                <div className="admin-users-content">
                    <div className="admin-logs-sticky-controls">
                        <div className="admin-users-toolbar">
                            <div>
                                <h2>System activity</h2>
                                <p>File and account activity across all users</p>
                            </div>
                            <div className="admin-log-toolbar-actions">
                                <span>{totalElements} {totalElements === 1 ? "event" : "events"}</span>
                                <button type="button" className="admin-export-button" onClick={exportJson} disabled={exporting}>
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                                        <path d="M6 3h9l3 3v15H6z" />
                                        <path d="M14 3v4h4M12 10v7M9 14l3 3 3-3" />
                                    </svg>
                                    {exporting ? "Exporting…" : "Export JSON"}
                                </button>
                            </div>
                        </div>

                        <div className="admin-log-filters" aria-label="Log filters">
                            <span className="admin-filter-icon" aria-hidden="true">
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                                    <path d="M4 5h16l-6.5 7.2V19l-3 1v-7.8L4 5Z" />
                                </svg>
                            </span>
                            <MultiFilter label="Users" options={userOptions} selected={selectedUsers} onToggle={(value) => toggleSelection(setSelectedUsers, value)} onClear={() => { setSelectedUsers([]); setPage(1); }} searchable />
                            <MultiFilter label="Actions" options={actionOptions} selected={selectedActions} onToggle={(value) => toggleSelection(setSelectedActions, value)} onClear={() => { setSelectedActions([]); setPage(1); }} searchable />
                            <label className="admin-time-filter"><span>From</span><input type="datetime-local" value={fromTime} max={toTime || undefined} onChange={(event) => { setFromTime(event.target.value); setPage(1); }} /></label>
                            <label className="admin-time-filter"><span>To</span><input type="datetime-local" value={toTime} min={fromTime || undefined} onChange={(event) => { setToTime(event.target.value); setPage(1); }} /></label>
                            {(selectedUsers.length > 0 || selectedActions.length > 0 || fromTime || toTime) && (
                                <button type="button" className="admin-clear-filters" onClick={() => { setSelectedUsers([]); setSelectedActions([]); setFromTime(""); setToTime(""); setPage(1); }}>Clear filters</button>
                            )}
                        </div>
                    </div>

                    {error && <div className="admin-message error" role="alert">{error}</div>}

                    <div className="admin-users-table-wrap">
                        <table className="admin-users-table admin-logs-table">
                            <thead>
                                <tr><th>Time</th><th>User</th><th>Action</th><th>Entity</th><th>Details</th></tr>
                            </thead>
                            <tbody>
                                {logs.map((log) => {
                                    const expandable = hasAdditionalDetails(log.details);
                                    const expanded = expandedLogs.has(log.id);
                                    return (
                                        <Fragment key={log.id}>
                                            <tr>
                                                <td className="admin-log-time">{formatTimestamp(log.timestamp)}</td>
                                                <td><strong>{log.username || "System"}</strong></td>
                                                <td><span className="admin-log-action">{humanize(log.action)}</span></td>
                                                <td>{log.entityType ? `${humanize(log.entityType)}${log.entityId ? ` #${log.entityId}` : ""}` : "—"}</td>
                                                <td>
                                                    <div className="admin-log-details-cell">
                                                        <span className="admin-log-details" title={log.details || ""}>{summarizeDetails(log.details)}</span>
                                                        {expandable && (
                                                            <button type="button" className={`admin-log-expand ${expanded ? "expanded" : ""}`} onClick={() => toggleLogDetails(log.id)} aria-expanded={expanded} aria-label={expanded ? "Hide log details" : "Show log details"}>
                                                                <svg viewBox="0 0 12 8" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m1 1.5 5 5 5-5" /></svg>
                                                            </button>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>
                                            {expanded && (
                                                <tr className="admin-log-expanded-row">
                                                    <td colSpan="5">
                                                        <dl className="admin-log-detail-grid">
                                                            {detailEntries(log.details).map(([key, value]) => (
                                                                <div key={key}><dt>{formatDetailLabel(key)}</dt><dd>{formatDetailValue(value)}</dd></div>
                                                            ))}
                                                        </dl>
                                                    </td>
                                                </tr>
                                            )}
                                        </Fragment>
                                    );
                                })}
                            </tbody>
                        </table>

                        {loading && <div className="admin-table-state">Loading logs…</div>}
                        {!loading && !error && logs.length === 0 && <div className="admin-table-state">No activity has been logged yet.</div>}
                    </div>

                    {!loading && !error && totalElements > 0 && (
                        <div className="admin-pagination">
                            <span>Showing {firstVisible}–{lastVisible} of {totalElements}</span>
                            <div className="admin-pagination-controls">
                                <label>
                                    Rows per page
                                    <select value={pageSize} onChange={(event) => { setPageSize(event.target.value); setPage(1); }}>
                                        <option value="10">10</option>
                                        <option value="25">25</option>
                                        <option value="50">50</option>
                                        <option value="all">All</option>
                                    </select>
                                </label>
                                <button type="button" onClick={() => setPage((value) => Math.max(1, value - 1))} disabled={currentPage === 1}>←</button>
                                <span>{pageSize === "all" ? "1 / 1" : `${currentPage} / ${totalPages}`}</span>
                                <button type="button" onClick={() => setPage((value) => Math.min(totalPages, value + 1))} disabled={currentPage === totalPages}>→</button>
                            </div>
                        </div>
                    )}
                </div>
            </section>
        </main>
    );
}
