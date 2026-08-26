import { useEffect, useState } from "react";
import DriveSidebar from "../components/DriveSidebar";
import NotificationMenu from "../components/NotificationMenu";
import UserMenu from "../components/UserMenu";
import { getLockboxStatus } from "../api/lockbox";

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

export default function Lockbox({ sidebarOpen, onToggleSidebar, onLogout }) {
    const [status, setStatus] = useState(null);
    const [statusError, setStatusError] = useState("");

    useEffect(() => {
        let cancelled = false;

        getLockboxStatus()
            .then((result) => {
                if (!cancelled) setStatus(result);
            })
            .catch((error) => {
                if (!cancelled) setStatusError(error.message || "Unable to check Lockbox status");
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
                        <span className="lockbox-intro-icon"><SafeIcon /></span>
                        <p className="lockbox-eyebrow">Private storage</p>
                        <h1>Your Lockbox</h1>
                        <p>Keep your most sensitive files protected in a dedicated, encrypted space.</p>

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
                </section>
            </main>
        </div>
    );
}
