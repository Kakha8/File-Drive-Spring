import { useEffect, useId, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
    getCurrentUsername,
    logout as apiLogout,
} from "../api/auth";

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

function SettingsIcon({ className }) {
    return (
        <Icon className={className}>
            <circle cx="12" cy="12" r="3" />
            <path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.83 2.83-.06-.06A1.7 1.7 0 0 0 15 19.4a1.7 1.7 0 0 0-1 .6 1.7 1.7 0 0 0-.4 1.1V21H9.6v-.1A1.7 1.7 0 0 0 8.5 19.4a1.7 1.7 0 0 0-1.88.34l-.06.06-2.83-2.83.06-.06A1.7 1.7 0 0 0 4.6 15a1.7 1.7 0 0 0-.6-1 1.7 1.7 0 0 0-1.1-.4H3V9.6h.1A1.7 1.7 0 0 0 4.6 8.5a1.7 1.7 0 0 0-.34-1.88l-.06-.06 2.83-2.83.06.06A1.7 1.7 0 0 0 9 4.6a1.7 1.7 0 0 0 1-.6 1.7 1.7 0 0 0 .4-1.1V3h4v.1A1.7 1.7 0 0 0 15.5 4.6a1.7 1.7 0 0 0 1.88-.34l.06-.06 2.83 2.83-.06.06A1.7 1.7 0 0 0 19.4 9a1.7 1.7 0 0 0 .6 1 1.7 1.7 0 0 0 1.1.4h.1v4h-.1A1.7 1.7 0 0 0 19.4 15Z" />
        </Icon>
    );
}

function LogoutIcon({ className }) {
    return (
        <Icon className={className}>
            <path d="M10 17l5-5-5-5" />
            <path d="M15 12H3" />
            <path d="M14 4h4a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3h-4" />
        </Icon>
    );
}

function getInitials(username) {
    const parts = username
        .trim()
        .split(/[\s._-]+/)
        .filter(Boolean);

    if (parts.length >= 2) {
        return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
    }

    return (parts[0] || "U").slice(0, 2).toUpperCase();
}

export default function UserMenu({ onLogout }) {
    const navigate = useNavigate();
    const menuId = useId();
    const menuRef = useRef(null);

    const [open, setOpen] = useState(false);
    const [signingOut, setSigningOut] = useState(false);

    const username = getCurrentUsername();

    const initials = useMemo(
        () => getInitials(username),
        [username]
    );

    useEffect(() => {
        if (!open) return undefined;

        function handleOutsideClick(event) {
            if (!menuRef.current?.contains(event.target)) {
                setOpen(false);
            }
        }

        function handleKeyDown(event) {
            if (event.key === "Escape") {
                setOpen(false);
            }
        }

        document.addEventListener("pointerdown", handleOutsideClick);
        document.addEventListener("keydown", handleKeyDown);

        return () => {
            document.removeEventListener("pointerdown", handleOutsideClick);
            document.removeEventListener("keydown", handleKeyDown);
        };
    }, [open]);

    function openSettings() {
        setOpen(false);
        navigate("/settings");
    }

    async function signOut() {
        if (signingOut) return;

        setOpen(false);
        setSigningOut(true);

        try {
            await apiLogout();
        } finally {
            setSigningOut(false);

            if (onLogout) {
                onLogout();
            }
        }
    }

    return (
        <div className="user-menu-root" ref={menuRef}>
            <button
                type="button"
                className="user-menu-trigger"
                aria-label={`Open account menu for ${username}`}
                aria-haspopup="menu"
                aria-expanded={open}
                aria-controls={menuId}
                onClick={() => setOpen((current) => !current)}
            >
                <span className="user-menu-avatar">{initials}</span>
            </button>

            {open && (
                <div
                    id={menuId}
                    className="user-menu-dropdown"
                    role="menu"
                >
                    <div className="user-menu-account">
                        <span className="user-menu-avatar user-menu-avatar-large">
                            {initials}
                        </span>

                        <div className="user-menu-account-text">
                            <strong>{username}</strong>
                            <span>Signed in</span>
                        </div>
                    </div>

                    <div className="user-menu-divider" role="separator" />

                    <button
                        type="button"
                        className="user-menu-item"
                        role="menuitem"
                        onClick={openSettings}
                    >
                        <SettingsIcon className="user-menu-item-icon" />
                        <span>Settings</span>
                    </button>

                    <div className="user-menu-divider" role="separator" />

                    <button
                        type="button"
                        className="user-menu-item user-menu-signout"
                        role="menuitem"
                        disabled={signingOut}
                        onClick={signOut}
                    >
                        <LogoutIcon className="user-menu-item-icon" />
                        <span>
                            {signingOut ? "Signing out..." : "Sign out"}
                        </span>
                    </button>
                </div>
            )}
        </div>
    );
}