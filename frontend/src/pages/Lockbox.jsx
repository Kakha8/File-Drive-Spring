import { useEffect, useState } from "react";
import DriveSidebar from "../components/DriveSidebar";
import NotificationMenu from "../components/NotificationMenu";
import UserMenu from "../components/UserMenu";
import { getLockboxFiles, getLockboxRevisions, getLockboxStatus, getRegisteredDevices } from "../api/lockbox";

function SafeIcon({ className = "" }) {
    return (
        <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <rect x="3.5" y="3.5" width="17" height="17" rx="2.5" />
            <circle cx="12" cy="12" r="3.25" />
            <path d="M12 8.75v2M12 15.25v-2M8.75 12h2M15.25 12h-2" />
            <path d="M6.5 20.5v1M17.5 20.5v1" />
        </svg>
    );
}

function formatDate(value, fallback = "Never") {
    if (!value) return fallback;
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return fallback;

    return new Intl.DateTimeFormat(undefined, {
        year: "numeric",
        month: "short",
        day: "numeric",
        hour: "numeric",
        minute: "2-digit",
    }).format(date);
}

function formatBytes(bytes) {
    if (bytes == null || Number.isNaN(Number(bytes))) return "—";
    if (Number(bytes) === 0) return "0 B";

    const units = ["B", "KB", "MB", "GB", "TB"];
    let value = Number(bytes);
    let unit = 0;

    while (value >= 1024 && unit < units.length - 1) {
        value /= 1024;
        unit += 1;
    }

    return `${value.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`;
}

function encryptedFileName(file) {
    return `${file.clientFileId}.fdlock`;
}

export default function Lockbox({ sidebarOpen, onToggleSidebar, onLogout }) {
    const [status, setStatus] = useState(null);
    const [statusError, setStatusError] = useState("");
    const [devices, setDevices] = useState([]);
    const [devicesLoading, setDevicesLoading] = useState(true);
    const [devicesError, setDevicesError] = useState("");
    const [files, setFiles] = useState([]);
    const [filesLoading, setFilesLoading] = useState(true);
    const [filesError, setFilesError] = useState("");
    const [expandedFileId, setExpandedFileId] = useState(null);
    const [revisionsByFile, setRevisionsByFile] = useState({});
    const [revisionLoadingId, setRevisionLoadingId] = useState(null);
    const [revisionErrors, setRevisionErrors] = useState({});

    useEffect(() => {
        let cancelled = false;

        getLockboxStatus()
            .then((result) => {
                if (!cancelled) setStatus(result);
            })
            .catch((error) => {
                if (!cancelled) setStatusError(error.message || "Unable to check Lockbox status");
            });

        getRegisteredDevices()
            .then((result) => {
                if (!cancelled) setDevices(result);
            })
            .catch((error) => {
                if (!cancelled) setDevicesError(error.message || "Unable to load registered devices");
            })
            .finally(() => {
                if (!cancelled) setDevicesLoading(false);
            });

        getLockboxFiles()
            .then((result) => {
                if (!cancelled) setFiles(result);
            })
            .catch((error) => {
                if (!cancelled) setFilesError(error.message || "Unable to load encrypted files");
            })
            .finally(() => {
                if (!cancelled) setFilesLoading(false);
            });

        return () => {
            cancelled = true;
        };
    }, []);

    const isActivated = status?.lockboxStatus === "ENABLED";
    const isSuspended = status?.lockboxStatus === "SUSPENDED";
    const totalEncryptedSize = files.reduce(
        (total, file) => total + (Number(file.containerSize) || 0),
        0
    );

    async function toggleRevisions(fileId) {
        if (expandedFileId === fileId) {
            setExpandedFileId(null);
            return;
        }

        setExpandedFileId(fileId);
        if (revisionsByFile[fileId]) return;

        setRevisionLoadingId(fileId);
        setRevisionErrors((current) => ({ ...current, [fileId]: "" }));

        try {
            const revisions = await getLockboxRevisions(fileId);
            setRevisionsByFile((current) => ({ ...current, [fileId]: revisions }));
        } catch (error) {
            setRevisionErrors((current) => ({
                ...current,
                [fileId]: error.message || "Unable to load revisions",
            }));
        } finally {
            setRevisionLoadingId((current) => current === fileId ? null : current);
        }
    }

    return (
        <div className="drive-page">
            <DriveSidebar active="lockbox" sidebarOpen={sidebarOpen} onToggleSidebar={onToggleSidebar} onLogoutComplete={onLogout} />

            <main className="drive-main">
                <header className="drive-header">
                    <div className="breadcrumbs"><strong>Lockbox</strong></div>
                    <div className="drive-header-actions">
                        <NotificationMenu onLogout={onLogout} />
                        <UserMenu onLogout={onLogout} />
                    </div>
                </header>

                <section className="lockbox-page-content">
                    <div className="lockbox-intro">
                        <div className="lockbox-intro-copy">
                            <span className="lockbox-intro-icon"><SafeIcon /></span>
                            <div>
                                <p className="lockbox-eyebrow">Private storage</p>
                                <h1>Your Lockbox</h1>
                                <p>Keep your most sensitive files protected in a dedicated, encrypted space.</p>
                            </div>
                        </div>

                        <div className={`lockbox-status ${isActivated ? "activated" : isSuspended ? "suspended" : "inactive"}`} role="status">
                            {!status && !statusError && (
                                <><span className="lockbox-status-dot loading" /><span><strong>Checking activation…</strong><small>Confirming your account status</small></span></>
                            )}
                            {statusError && (
                                <><span className="lockbox-status-dot error" /><span><strong>Status unavailable</strong><small>{statusError}</small></span></>
                            )}
                            {status && isActivated && (
                                <><span className="lockbox-status-dot" /><span><strong>Lockbox is activated</strong><small>Protected storage is enabled on your account</small></span></>
                            )}
                            {status && isSuspended && (
                                <><span className="lockbox-status-dot" /><span><strong>Lockbox is suspended</strong><small>Protected storage is currently unavailable</small></span></>
                            )}
                            {status && !isActivated && !isSuspended && (
                                <><span className="lockbox-status-dot" /><span><strong>Lockbox is not activated</strong><small>Protected storage has not been enabled on your account</small></span></>
                            )}
                        </div>
                    </div>

                    <section className="lockbox-files" aria-labelledby="encrypted-files-title">
                        <div className="lockbox-section-heading">
                            <div>
                                <p className="lockbox-eyebrow">Encrypted storage</p>
                                <h2 id="encrypted-files-title">Files</h2>
                            </div>
                            {!filesLoading && !filesError && (
                                <span>
                                    {files.length} {files.length === 1 ? "file" : "files"}
                                    <span className="lockbox-summary-divider">•</span>
                                    {formatBytes(totalEncryptedSize)} total
                                </span>
                            )}
                        </div>

                        <div className="lockbox-file-table">
                            <div className="lockbox-file-row lockbox-file-head">
                                <span>Encrypted filename</span><span>Size</span><span>Time uploaded</span>
                            </div>
                            {filesLoading && <div className="lockbox-table-message">Loading encrypted files…</div>}
                            {!filesLoading && filesError && <div className="lockbox-table-message error">{filesError}</div>}
                            {!filesLoading && !filesError && files.length === 0 && <div className="lockbox-table-message">No encrypted files uploaded yet.</div>}
                            {!filesLoading && !filesError && files.map((file) => {
                                const expanded = expandedFileId === file.id;
                                const revisions = revisionsByFile[file.id] || [];

                                return <div className="lockbox-file-entry" key={file.id}>
                                    <div className={`lockbox-file-row ${expanded ? "expanded" : ""}`}>
                                        <div className="lockbox-file-name">
                                            {Number(file.revision) > 1 ? (
                                                <button
                                                    type="button"
                                                    className="lockbox-revision-toggle"
                                                    onClick={() => toggleRevisions(file.id)}
                                                    aria-expanded={expanded}
                                                    aria-label={`${expanded ? "Hide" : "Show"} revisions for ${encryptedFileName(file)}`}
                                                >
                                                    <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m7 5 5 5-5 5" /></svg>
                                                </button>
                                            ) : null}
                                            <strong title={encryptedFileName(file)}>{encryptedFileName(file)}</strong>
                                        </div>
                                        <span>{formatBytes(file.containerSize)}</span>
                                        <span>{formatDate(file.createdAt, "Unknown")}</span>
                                    </div>

                                    {expanded && (
                                        <div className="lockbox-revisions">
                                            {revisionLoadingId === file.id && <div className="lockbox-revision-message">Loading revisions…</div>}
                                            {revisionErrors[file.id] && <div className="lockbox-revision-message error">{revisionErrors[file.id]}</div>}
                                            {revisionLoadingId !== file.id && !revisionErrors[file.id] && revisions.map((revision) => (
                                                <div className="lockbox-revision-row" key={revision.revision}>
                                                    <span>
                                                        Revision {revision.revision}
                                                        {revision.current && <small>Current</small>}
                                                    </span>
                                                    <span>{formatBytes(revision.containerSize)}</span>
                                                    <span>{formatDate(revision.createdAt, "Unknown")}</span>
                                                </div>
                                            ))}
                                            {revisionLoadingId !== file.id && !revisionErrors[file.id] && revisions.length === 0 && <div className="lockbox-revision-message">No revisions found.</div>}
                                        </div>
                                    )}
                                </div>
                            })}
                        </div>
                    </section>

                    <section className="lockbox-devices" aria-labelledby="registered-devices-title">
                        <div className="lockbox-section-heading">
                            <div>
                                <p className="lockbox-eyebrow">Account security</p>
                                <h2 id="registered-devices-title">Registered devices</h2>
                            </div>
                            {!devicesLoading && !devicesError && <span>{devices.length} {devices.length === 1 ? "device" : "devices"}</span>}
                        </div>

                        <div className="lockbox-device-table">
                            <div className="lockbox-device-row lockbox-device-head">
                                <span>Device name</span><span>Registration date</span><span>Last seen</span><span>Status</span>
                            </div>
                            {devicesLoading && <div className="lockbox-device-message">Loading registered devices…</div>}
                            {!devicesLoading && devicesError && <div className="lockbox-device-message error">{devicesError}</div>}
                            {!devicesLoading && !devicesError && devices.length === 0 && <div className="lockbox-device-message">No registered devices yet.</div>}
                            {!devicesLoading && !devicesError && devices.map((device) => {
                                const active = device.deviceStatus === "ACTIVE";
                                return (
                                    <div className="lockbox-device-row" key={device.deviceId}>
                                        <strong>{device.deviceName}</strong>
                                        <span>{formatDate(device.registeredAt, "Unknown")}</span>
                                        <span>{formatDate(device.lastSeenAt)}</span>
                                        <span><span className={`device-status-badge ${active ? "active" : "revoked"}`}>{active ? "Active" : "Revoked"}</span></span>
                                    </div>
                                );
                            })}
                        </div>
                    </section>
                </section>
            </main>
        </div>
    );
}
