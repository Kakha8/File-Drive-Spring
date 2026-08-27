import { useEffect, useState } from "react";
import DriveSidebar from "../components/DriveSidebar";
import NotificationMenu from "../components/NotificationMenu";
import UserMenu from "../components/UserMenu";
import { getLockboxStatus, getRegisteredDevices } from "../api/lockbox";

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

export default function Lockbox({ sidebarOpen, onToggleSidebar, onLogout }) {
    const [status, setStatus] = useState(null);
    const [statusError, setStatusError] = useState("");
    const [devices, setDevices] = useState([]);
    const [devicesLoading, setDevicesLoading] = useState(true);
    const [devicesError, setDevicesError] = useState("");

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

        return () => {
            cancelled = true;
        };
    }, []);

    const isActivated = status?.lockboxStatus === "ENABLED";
    const isSuspended = status?.lockboxStatus === "SUSPENDED";

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
