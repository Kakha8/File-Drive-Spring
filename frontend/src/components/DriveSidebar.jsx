import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { logout as apiLogout } from "../api/auth";

const SIDEBAR_STORAGE_KEY = "drive-sidebar-open";

function Icon({ children, className = "" }) {
    return (
        <svg
            className={className}
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
        >
            {children}
        </svg>
    );
}

const Icons = {
    Menu: ({ className }) => (
        <Icon className={className}>
            <path d="M4 7h16" />
            <path d="M4 12h16" />
            <path d="M4 17h16" />
        </Icon>
    ),
    Logout: ({ className }) => (
        <Icon className={className}>
            <path d="M10 17l5-5-5-5" />
            <path d="M15 12H3" />
            <path d="M14 4h4a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3h-4" />
        </Icon>
    ),
    File: ({ className }) => (
        <Icon className={className}>
            <path d="M6 3h8l4 4v14H6z" />
            <path d="M14 3v5h5" />
        </Icon>
    ),
    Shared: ({ className }) => (
        <Icon className={className}>
            <path d="M16 21v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2" />
            <circle cx="9.5" cy="7" r="4" />
            <path d="M22 21v-2a4 4 0 0 0-3-3.8" />
        </Icon>
    ),
    Clock: ({ className }) => (
        <Icon className={className}>
            <circle cx="12" cy="12" r="9" />
            <path d="M12 7v5l3 2" />
        </Icon>
    ),
    Star: ({ className }) => (
        <Icon className={className}>
            <path d="M12 3.5 14.7 9l6 .9-4.35 4.25 1.05 6L12 17.3l-5.4 2.85 1.05-6L3.3 9.9l6-.9z" />
        </Icon>
    ),
    Archive: ({ className }) => (
        <Icon className={className}>
            <path d="M4 7h16v13H4z" />
            <path d="M3 3h18v4H3z" />
            <path d="M10 11h4" />
        </Icon>
    ),
    Trash: ({ className }) => (
        <Icon className={className}>
            <path d="M3 6h18" />
            <path d="M8 6V4h8v2" />
            <path d="M6 6l1 15h10l1-15" />
        </Icon>
    ),
};

const navItems = [
    { key: "my", label: "My files", icon: Icons.File, path: "/main" },
    { key: "shared", label: "Shared", icon: Icons.Shared, path: null },
    { key: "recent", label: "Recent", icon: Icons.Clock, path: null },
    { key: "favorites", label: "Favorites", icon: Icons.Star, path: null },
    { key: "archived", label: "Archived", icon: Icons.Archive, path: null },
    { key: "trash", label: "Trash", icon: Icons.Trash, path: "/trashcan" },
];

function getSavedSidebarOpen(defaultOpen) {
    const saved = window.localStorage.getItem(SIDEBAR_STORAGE_KEY);

    if (saved === "true") return true;
    if (saved === "false") return false;

    return defaultOpen;
}

export default function DriveSidebar({
                                         active = "my",
                                         sidebarOpen,
                                         onToggleSidebar,
                                         defaultOpen = false,
                                         username = "admin",
                                         locationLabel = "admin",
                                         deletedCount = null,
                                         onLogoutComplete,
                                     }) {
    const navigate = useNavigate();

    const [internalOpen, setInternalOpen] = useState(() =>
        getSavedSidebarOpen(defaultOpen)
    );
    const [logoutConfirm, setLogoutConfirm] = useState(false);
    const [loggingOut, setLoggingOut] = useState(false);

    const isControlled = typeof sidebarOpen === "boolean";
    const isOpen = isControlled ? sidebarOpen : internalOpen;

    useEffect(() => {
        window.localStorage.setItem(SIDEBAR_STORAGE_KEY, String(isOpen));
    }, [isOpen]);

    function toggleSidebar() {
        if (onToggleSidebar) {
            onToggleSidebar();
            return;
        }

        setInternalOpen((current) => !current);
    }

    function handleNavClick(item) {
        setLogoutConfirm(false);

        if (item.path) {
            navigate(item.path);
        }
    }

    async function handleLogout() {
        if (!logoutConfirm) {
            setLogoutConfirm(true);
            return;
        }

        try {
            setLoggingOut(true);
            await apiLogout();

            if (onLogoutComplete) {
                onLogoutComplete();
            }

            navigate("/login", { replace: true });
        } catch {
            if (onLogoutComplete) {
                onLogoutComplete();
            }

            navigate("/login", { replace: true });
        } finally {
            setLoggingOut(false);
        }
    }

    return (
        <aside className={`drive-sidebar ${isOpen ? "open" : "closed"}`}>
            <div className="sidebar-top">
                <button
                    type="button"
                    className="menu-button"
                    onClick={toggleSidebar}
                    aria-label={isOpen ? "Collapse sidebar" : "Expand sidebar"}
                    title={isOpen ? "Collapse sidebar" : "Expand sidebar"}
                >
                    <Icons.Menu className="svg-icon" />
                </button>

                {isOpen && (
                    <>
                        <div className="workspace-mark">FS</div>
                        <div className="workspace-text">
                            <strong>File-Drive-Spring</strong>
                            <span>{username}</span>
                        </div>
                    </>
                )}
            </div>

            <div className="sidebar-scroll">
                <nav className="sidebar-nav">
                    {navItems.map((item) => {
                        const NavIcon = item.icon;

                        return (
                            <button
                                key={item.key}
                                type="button"
                                className={`nav-item ${
                                    active === item.key ? "active" : ""
                                }`}
                                onClick={() => handleNavClick(item)}
                                title={!isOpen ? item.label : undefined}
                            >
                                <span className="nav-icon">
                                    <NavIcon className="svg-icon" />
                                </span>

                                {isOpen && <span>{item.label}</span>}
                            </button>
                        );
                    })}
                </nav>

                {isOpen && active === "my" && (
                    <div className="label-section">
                        <p>Location</p>
                        <button type="button">
                            <span className="dot final-dot" />
                            <span>{locationLabel}</span>
                        </button>
                    </div>
                )}

                {isOpen && active === "trash" && deletedCount !== null && (
                    <div className="label-section">
                        <p>TRASHCAN</p>
                        <button type="button">
                            <span className="dot final-dot" />
                            <span>{deletedCount} deleted items</span>
                        </button>
                    </div>
                )}
            </div>

            <div className="sidebar-footer">
                <button
                    type="button"
                    className={`logout-action ${logoutConfirm ? "confirming" : ""}`}
                    onClick={handleLogout}
                    disabled={loggingOut}
                    title={logoutConfirm ? "Click again to log out" : "Log out"}
                >
                    <Icons.Logout className="svg-icon" />

                    {isOpen && (
                        <span>
                            {loggingOut
                                ? "Logging out..."
                                : logoutConfirm
                                    ? "Confirm logout"
                                    : "Log out"}
                        </span>
                    )}
                </button>

                {isOpen && active === "trash" && (
                    <div className="storage-box">
                        <div className="storage-bar">
                            <div />
                        </div>
                        <p>Trash uses your storage until emptied.</p>
                    </div>
                )}

                {isOpen && active === "my" && (
                    <div className="storage-box">
                        <div className="storage-bar">
                            <div />
                        </div>
                        <p>2.1 GB of 15 GB used</p>
                    </div>
                )}
            </div>
        </aside>
    );
}
