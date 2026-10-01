import DriveSidebar from "../components/DriveSidebar";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getAdminUsers } from "../api/users";

export default function AdminDashboard({
    onLogout,
    sidebarOpen,
    onToggleSidebar,
}) {
    const navigate = useNavigate();
    const [userCount, setUserCount] = useState(null);

    useEffect(() => {
        let cancelled = false;

        getAdminUsers()
            .then((users) => {
                if (!cancelled) setUserCount(users.length);
            })
            .catch(() => {
                if (!cancelled) setUserCount(null);
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

                    <div className="admin-dashboard-placeholder">
                        <h2>More dashboard tools are coming soon</h2>
                        <p>Storage, quarantine, audit activity, and system health will appear here.</p>
                    </div>
                </div>
            </section>
        </main>
    );
}
