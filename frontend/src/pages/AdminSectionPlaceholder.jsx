import { useNavigate } from "react-router-dom";
import DriveSidebar from "../components/DriveSidebar";

export default function AdminSectionPlaceholder({ title, description, onLogout, sidebarOpen, onToggleSidebar }) {
    const navigate = useNavigate();
    return <main className="drive-page admin-dashboard-page">
        <DriveSidebar active="admin" sidebarOpen={sidebarOpen} onToggleSidebar={onToggleSidebar} onLogoutComplete={onLogout} />
        <section className="drive-main">
            <header className="drive-header admin-dashboard-header"><div className="admin-page-title"><button type="button" onClick={() => navigate("/admin")} aria-label="Back to dashboard">←</button><h1>{title}</h1></div></header>
            <div className="admin-dashboard-content"><div className="admin-dashboard-placeholder"><h2>{title} details are coming soon</h2><p>{description}</p></div></div>
        </section>
    </main>;
}
