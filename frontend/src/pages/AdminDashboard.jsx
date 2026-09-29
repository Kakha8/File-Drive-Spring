import DriveSidebar from "../components/DriveSidebar";

export default function AdminDashboard({
    onLogout,
    sidebarOpen,
    onToggleSidebar,
}) {
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

                <div className="admin-coming-soon">
                    <div className="admin-coming-soon-icon" aria-hidden="true">
                        <svg
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="1.8"
                            strokeLinecap="round"
                            strokeLinejoin="round"
                        >
                            <path d="M4 17a8 8 0 1 1 16 0" />
                            <path d="m12 17 4-5" />
                            <circle cx="12" cy="17" r="1" />
                        </svg>
                    </div>
                    <h1>Admin dashboard</h1>
                    <p>Coming soon</p>
                </div>
            </section>
        </main>
    );
}
