import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getCurrentUsername } from "../api/auth";
import {
    createAdminUser,
    deleteAdminUser,
    getAdminUsers,
    removeAdminUserTwoFactor,
    resetAdminUserPassword,
    restoreAdminUser,
    setAdminUserSuspended,
    updateAdminUser,
} from "../api/users";
import DriveSidebar from "../components/DriveSidebar";
import NotificationMenu from "../components/NotificationMenu";
import PasswordInput from "../components/PasswordInput";
import UserMenu from "../components/UserMenu";

function UserPlusIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M15 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
            <circle cx="8.5" cy="7" r="4" />
            <path d="M19 8v6M16 11h6" />
        </svg>
    );
}

function ClipboardIcon() {
    return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><rect x="6" y="5" width="13" height="16" rx="2" /><path d="M9 5V3h7v2M3 17V5a2 2 0 0 1 2-2" /></svg>;
}

function RefreshIcon() {
    return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M20 6v5h-5" /><path d="M4 18v-5h5" /><path d="M6.1 9a7 7 0 0 1 11.5-2.4L20 11M4 13l2.4 4.4A7 7 0 0 0 17.9 15" /></svg>;
}

function generateTemporaryPassword() {
    const groups = ["ABCDEFGHJKLMNPQRSTUVWXYZ", "abcdefghijkmnopqrstuvwxyz", "23456789", "!@#$%&*"];
    const all = groups.join("");
    const randomIndex = (length) => {
        const values = new Uint32Array(1);
        crypto.getRandomValues(values);
        return values[0] % length;
    };
    const characters = groups.map((group) => group[randomIndex(group.length)]);
    while (characters.length < 16) characters.push(all[randomIndex(all.length)]);
    for (let index = characters.length - 1; index > 0; index -= 1) {
        const swapIndex = randomIndex(index + 1);
        [characters[index], characters[swapIndex]] = [characters[swapIndex], characters[index]];
    }
    return characters.join("");
}

async function copyToClipboard(value) {
    if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(value);
        return;
    }
    const textarea = document.createElement("textarea");
    textarea.value = value;
    textarea.style.position = "fixed";
    textarea.style.opacity = "0";
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand("copy");
    textarea.remove();
}

const SUSPENSION_OPTIONS = [
    { value: "ONE_DAY", label: "1 day", days: 1 },
    { value: "THREE_DAYS", label: "3 days", days: 3 },
    { value: "ONE_WEEK", label: "1 week", days: 7 },
    { value: "INDEFINITE", label: "Indefinite", days: null },
];

function suspensionBadge(user) {
    if (!user.suspended) return "";
    if (!user.suspendedUntil) return "Suspended indefinitely";
    const remaining = new Date(user.suspendedUntil).getTime() - Date.now();
    if (remaining <= 0) return "Suspension ending";
    const hours = Math.ceil(remaining / 3_600_000);
    return hours > 48 ? `Suspended · ${Math.ceil(hours / 24)}d remaining` : `Suspended · ${hours}h remaining`;
}

function suspensionExpiry(duration) {
    const option = SUSPENSION_OPTIONS.find((item) => item.value === duration);
    if (!option || option.days == null) return "Access remains blocked until an administrator reactivates the account.";
    const expires = new Date(Date.now() + option.days * 86_400_000);
    return `Access will resume automatically on ${expires.toLocaleString([], { dateStyle: "medium", timeStyle: "short" })}.`;
}

function accountStatus(user) {
    if (user.deleted) return "DELETED";
    if (user.suspended) return "SUSPENDED";
    return "ACTIVE";
}

function formatCreatedAt(value) {
    if (!value) return "Unknown";
    return new Intl.DateTimeFormat(undefined, {
        dateStyle: "medium",
        timeStyle: "short",
    }).format(new Date(value));
}

function StatusFilter({ value, onChange }) {
    const detailsRef = useRef(null);
    const options = [
        ["ALL", "All"],
        ["ACTIVE", "Active"],
        ["SUSPENDED", "Suspended"],
        ["DELETED", "Deleted"],
    ];
    const currentLabel = options.find(([option]) => option === value)?.[1] || "All";

    useEffect(() => {
        const close = (event) => {
            if (detailsRef.current?.open && !detailsRef.current.contains(event.target)) {
                detailsRef.current.removeAttribute("open");
            }
        };
        document.addEventListener("pointerdown", close);
        return () => document.removeEventListener("pointerdown", close);
    }, []);

    return (
        <details ref={detailsRef} className="admin-status-filter">
            <summary><span>Status</span><strong>{currentLabel}</strong><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="m7 10 5 5 5-5" /></svg></summary>
            <div className="admin-status-filter-menu">
                {options.map(([option, label]) => (
                    <button key={option} type="button" className={value === option ? "selected" : ""} onClick={() => { onChange(option); detailsRef.current?.removeAttribute("open"); }}>{label}</button>
                ))}
            </div>
        </details>
    );
}

export default function AdminUsers({ onLogout, sidebarOpen, onToggleSidebar }) {
    const navigate = useNavigate();
    const currentUsername = getCurrentUsername();
    const [users, setUsers] = useState([]);
    const [query, setQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState("ALL");
    const [createdFrom, setCreatedFrom] = useState("");
    const [createdTo, setCreatedTo] = useState("");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [showCreate, setShowCreate] = useState(false);
    const [creating, setCreating] = useState(false);
    const [createOneTime, setCreateOneTime] = useState(true);
    const [passwordMode, setPasswordMode] = useState("generated");
    const [generatedPassword, setGeneratedPassword] = useState(() => generateTemporaryPassword());
    const [manualPassword, setManualPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [createFormError, setCreateFormError] = useState("");
    const [passwordCopied, setPasswordCopied] = useState(false);
    const [deleteTarget, setDeleteTarget] = useState(null);
    const [deleting, setDeleting] = useState(false);
    const [restoreTarget, setRestoreTarget] = useState(null);
    const [restoring, setRestoring] = useState(false);
    const [editTarget, setEditTarget] = useState(null);
    const [savingEdit, setSavingEdit] = useState(false);
    const [editTab, setEditTab] = useState("account");
    const [editTemporaryPassword, setEditTemporaryPassword] = useState(() => generateTemporaryPassword());
    const [editPasswordCopied, setEditPasswordCopied] = useState(false);
    const [savingSecurity, setSavingSecurity] = useState(false);
    const [securityMessage, setSecurityMessage] = useState("");
    const [securityError, setSecurityError] = useState("");
    const [confirmRemoveTwoFactor, setConfirmRemoveTwoFactor] = useState(false);
    const [suspensionTarget, setSuspensionTarget] = useState(null);
    const [savingSuspension, setSavingSuspension] = useState(false);
    const [suspensionDuration, setSuspensionDuration] = useState("ONE_DAY");
    const [openMenuId, setOpenMenuId] = useState(null);
    const [pageSize, setPageSize] = useState("10");
    const [page, setPage] = useState(1);

    const loadUsers = useCallback(async () => {
        setLoading(true);
        setError("");

        try {
            setUsers(await getAdminUsers());
        } catch (requestError) {
            setError(requestError.message || "Failed to load users");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        let cancelled = false;

        getAdminUsers()
            .then((loadedUsers) => {
                if (!cancelled) setUsers(loadedUsers);
            })
            .catch((requestError) => {
                if (!cancelled) {
                    setError(requestError.message || "Failed to load users");
                }
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });

        return () => {
            cancelled = true;
        };
    }, []);

    useEffect(() => {
        const closeMenu = (event) => {
            if (!event.target.closest(".admin-user-actions-menu")) setOpenMenuId(null);
        };
        document.addEventListener("pointerdown", closeMenu);
        return () => document.removeEventListener("pointerdown", closeMenu);
    }, []);

    const filteredUsers = useMemo(() => {
        const normalizedQuery = query.trim().toLowerCase();
        return users.filter((user) => {
            const matchesQuery = !normalizedQuery || user.username?.toLowerCase().includes(normalizedQuery);
            const matchesStatus = statusFilter === "ALL" || accountStatus(user) === statusFilter;
            const createdTime = user.createdAt ? new Date(user.createdAt).getTime() : null;
            const fromTime = createdFrom ? new Date(`${createdFrom}T00:00:00`).getTime() : null;
            const toTime = createdTo ? new Date(`${createdTo}T23:59:59.999`).getTime() : null;
            const matchesCreatedFrom = fromTime == null || (createdTime != null && createdTime >= fromTime);
            const matchesCreatedTo = toTime == null || (createdTime != null && createdTime <= toTime);
            return matchesQuery && matchesStatus && matchesCreatedFrom && matchesCreatedTo;
        });
    }, [createdFrom, createdTo, query, statusFilter, users]);

    const numericPageSize = pageSize === "all" ? Math.max(filteredUsers.length, 1) : Number(pageSize);
    const totalPages = Math.max(1, Math.ceil(filteredUsers.length / numericPageSize));
    const currentPage = Math.min(page, totalPages);
    const visibleUsers = pageSize === "all"
        ? filteredUsers
        : filteredUsers.slice((currentPage - 1) * numericPageSize, currentPage * numericPageSize);
    const firstVisible = filteredUsers.length === 0 ? 0 : (currentPage - 1) * numericPageSize + 1;
    const lastVisible = Math.min(currentPage * numericPageSize, filteredUsers.length);
    const filtersActive = Boolean(query || statusFilter !== "ALL" || createdFrom || createdTo);

    async function handleCreate(event) {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        const username = String(form.get("username") || "").trim();
        const usingGeneratedPassword = createOneTime && passwordMode === "generated";
        const password = usingGeneratedPassword ? generatedPassword : manualPassword;
        const role = String(form.get("role") || "USER");
        const passwordChangeRequired = createOneTime;

        if (!usingGeneratedPassword && manualPassword !== confirmPassword) {
            setCreateFormError("Passwords do not match.");
            return;
        }
        if (password.length < 8) {
            setCreateFormError("Password must contain at least 8 characters.");
            return;
        }

        setCreating(true);
        setCreateFormError("");
        setError("");

        try {
            await createAdminUser({ username, password, role, passwordChangeRequired });
            setShowCreate(false);
            await loadUsers();
        } catch (requestError) {
            setCreateFormError(requestError.message || "Failed to create user");
        } finally {
            setCreating(false);
        }
    }

    function openCreateModal() {
        setCreateOneTime(true);
        setPasswordMode("generated");
        setGeneratedPassword(generateTemporaryPassword());
        setManualPassword("");
        setConfirmPassword("");
        setCreateFormError("");
        setPasswordCopied(false);
        setShowCreate(true);
    }

    async function handleCopyPassword() {
        try {
            await copyToClipboard(generatedPassword);
            setPasswordCopied(true);
            window.setTimeout(() => setPasswordCopied(false), 1600);
        } catch {
            setCreateFormError("Could not copy the password. Select it and copy it manually.");
        }
    }

    async function handleDelete() {
        if (!deleteTarget) return;
        setDeleting(true);
        setError("");

        try {
            await deleteAdminUser(deleteTarget.id);
            setDeleteTarget(null);
            await loadUsers();
        } catch (requestError) {
            setError(requestError.message || "Failed to delete user");
        } finally {
            setDeleting(false);
        }
    }

    async function handleRestore() {
        if (!restoreTarget) return;
        setRestoring(true);
        setError("");
        try {
            await restoreAdminUser(restoreTarget.id);
            setRestoreTarget(null);
            await loadUsers();
        } catch (requestError) {
            setError(requestError.message || "Failed to restore user");
        } finally {
            setRestoring(false);
        }
    }

    async function handleEdit(event) {
        event.preventDefault();
        if (!editTarget) return;
        const form = new FormData(event.currentTarget);
        setSavingEdit(true);
        setError("");
        try {
            const updated = await updateAdminUser(editTarget.id, {
                username: String(form.get("username") || "").trim(),
                role: editTarget.username === currentUsername ? editTarget.role : String(form.get("role") || "USER"),
            });
            setEditTarget(updated);
            setSecurityMessage("Account details saved.");
            await loadUsers();
        } catch (requestError) {
            setSecurityError(requestError.message || "Failed to update user");
        } finally {
            setSavingEdit(false);
        }
    }

    function openEditModal(user) {
        setEditTarget(user);
        setEditTab("account");
        setEditTemporaryPassword(generateTemporaryPassword());
        setEditPasswordCopied(false);
        setSecurityMessage("");
        setSecurityError("");
        setConfirmRemoveTwoFactor(false);
    }

    async function copyEditTemporaryPassword() {
        try {
            await copyToClipboard(editTemporaryPassword);
            setEditPasswordCopied(true);
            window.setTimeout(() => setEditPasswordCopied(false), 1600);
        } catch {
            setSecurityError("Could not copy the password. Select it and copy it manually.");
        }
    }

    async function handlePasswordReset() {
        if (!editTarget) return;
        setSavingSecurity(true);
        setSecurityError("");
        setSecurityMessage("");
        try {
            const updated = await resetAdminUserPassword(editTarget.id, editTemporaryPassword);
            setEditTarget(updated);
            setSecurityMessage("Password reset. The user must choose a new password at next sign-in. Existing login 2FA and refresh sessions were removed.");
            await loadUsers();
        } catch (requestError) {
            setSecurityError(requestError.message || "Failed to reset password");
        } finally {
            setSavingSecurity(false);
        }
    }

    async function handleRemoveTwoFactor() {
        if (!editTarget) return;
        setSavingSecurity(true);
        setSecurityError("");
        setSecurityMessage("");
        try {
            const updated = await removeAdminUserTwoFactor(editTarget.id);
            setEditTarget(updated);
            setConfirmRemoveTwoFactor(false);
            setSecurityMessage("Two-factor authentication removed. The user’s refresh sessions were revoked.");
            await loadUsers();
        } catch (requestError) {
            setSecurityError(requestError.message || "Failed to remove two-factor authentication");
        } finally {
            setSavingSecurity(false);
        }
    }

    async function handleSuspension() {
        if (!suspensionTarget) return;
        setSavingSuspension(true);
        setError("");
        try {
            await setAdminUserSuspended(suspensionTarget.id, !suspensionTarget.suspended, suspensionDuration);
            setSuspensionTarget(null);
            await loadUsers();
        } catch (requestError) {
            setError(requestError.message || "Failed to change account status");
        } finally {
            setSavingSuspension(false);
        }
    }

    return (
        <main className="drive-page admin-users-page">
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
                        <div>
                            <h1>Users</h1>
                        </div>
                    </div>
                    <div className="drive-header-actions">
                        <NotificationMenu onLogout={onLogout} />
                        <UserMenu onLogout={onLogout} />
                    </div>
                </header>

                <div className="admin-users-content">
                    <div className="admin-users-toolbar">
                        <div className="admin-users-toolbar-filters">
                            <label className="admin-search">
                                <span aria-hidden="true">⌕</span>
                                <input
                                    type="search"
                                    value={query}
                                    onChange={(event) => {
                                        setQuery(event.target.value);
                                        setPage(1);
                                    }}
                                    placeholder="Search users"
                                    aria-label="Search users"
                                />
                            </label>
                            <StatusFilter value={statusFilter} onChange={(value) => { setStatusFilter(value); setPage(1); }} />
                            <label className="admin-created-filter"><span>Created from</span><input type="date" value={createdFrom} onChange={(event) => { setCreatedFrom(event.target.value); setPage(1); }} /></label>
                            <label className="admin-created-filter"><span>Created to</span><input type="date" value={createdTo} min={createdFrom || undefined} onChange={(event) => { setCreatedTo(event.target.value); setPage(1); }} /></label>
                            {filtersActive && <button type="button" className="admin-clear-user-filters" onClick={() => { setQuery(""); setStatusFilter("ALL"); setCreatedFrom(""); setCreatedTo(""); setPage(1); }}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" /></svg>Clear</button>}
                        </div>
                        <div className="admin-users-toolbar-actions">
                            <span>{filteredUsers.length === users.length ? users.length : `${filteredUsers.length} of ${users.length}`} {users.length === 1 ? "user" : "users"}</span>
                            <button type="button" className="admin-primary-button" onClick={openCreateModal}>
                                <UserPlusIcon />
                                Add user
                            </button>
                        </div>
                    </div>

                    {error && <div className="admin-message error" role="alert">{error}</div>}

                    <div className="admin-users-table-wrap">
                        <table className="admin-users-table">
                            <thead>
                                <tr><th>User</th><th>Role</th><th>Status</th><th>Created at</th><th className="admin-actions-column">Actions</th></tr>
                            </thead>
                            <tbody>
                                {visibleUsers.map((user) => {
                                    const isCurrentUser = user.username === currentUsername;
                                    const status = accountStatus(user);
                                    return (
                                        <tr key={user.id} className={user.deleted ? "admin-user-row-deleted" : ""}>
                                            <td>
                                                <div className="admin-user-cell">
                                                    <span className="admin-user-avatar">{user.username?.slice(0, 1).toUpperCase()}</span>
                                                    <span><strong>{user.username}</strong>{isCurrentUser && <small>You</small>}</span>
                                                </div>
                                            </td>
                                            <td><span className={`admin-role-badge ${user.role === "ADMIN" ? "admin" : ""}`}>{user.role || "USER"}</span></td>
                                            <td><span className={`admin-account-status ${status.toLowerCase()}`} title={user.deletedAt ? `Deleted ${new Date(user.deletedAt).toLocaleString()}` : user.suspendedUntil ? `Suspended until ${new Date(user.suspendedUntil).toLocaleString()}` : undefined}>{status === "ACTIVE" ? "Active" : status === "SUSPENDED" ? suspensionBadge(user) : "Deleted"}</span></td>
                                            <td className="admin-user-created-at">{formatCreatedAt(user.createdAt)}</td>
                                            <td className="admin-actions-column">
                                                <div className={`admin-user-actions-menu ${openMenuId === user.id ? "open" : ""}`}>
                                                    <button type="button" className="admin-user-actions-trigger" onClick={(event) => { event.stopPropagation(); setOpenMenuId((current) => current === user.id ? null : user.id); }} aria-label={`Actions for ${user.username}`} aria-expanded={openMenuId === user.id}><svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><circle cx="12" cy="5" r="1.8" /><circle cx="12" cy="12" r="1.8" /><circle cx="12" cy="19" r="1.8" /></svg></button>
                                                    {openMenuId === user.id && <div className="admin-user-actions-popover">
                                                        {user.deleted ? <button type="button" onClick={() => { setRestoreTarget(user); setOpenMenuId(null); }}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M3 12a9 9 0 1 0 3-6.7" /><path d="M3 4v6h6" /></svg>Restore</button> : <>
                                                        <button type="button" disabled={isCurrentUser || user.deleted} title={user.deleted ? "Deleted accounts cannot be edited" : isCurrentUser ? "Manage your own account from account settings" : undefined} onClick={() => { openEditModal(user); setOpenMenuId(null); }}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M12 20h9" /><path d="m16.5 3.5 4 4L8 20l-5 1 1-5Z" /></svg>Edit</button>
                                                        <button type="button" disabled={isCurrentUser || user.deleted} title={user.deleted ? "Deleted accounts cannot be suspended" : isCurrentUser ? "You cannot suspend your own account" : undefined} onClick={() => { setSuspensionDuration("ONE_DAY"); setSuspensionTarget(user); setOpenMenuId(null); }}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{user.suspended ? <><path d="M8 5v14l11-7Z" /><circle cx="12" cy="12" r="10" /></> : <><circle cx="12" cy="12" r="10" /><path d="M9 8v8M15 8v8" /></>}</svg>{user.suspended ? "Reactivate" : "Suspend"}</button>
                                                        <button type="button" className="danger" disabled={isCurrentUser || user.deleted} title={user.deleted ? "This account is already deleted" : isCurrentUser ? "You cannot delete your own account" : undefined} onClick={() => { setDeleteTarget(user); setOpenMenuId(null); }}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6M10 10v6M14 10v6" /></svg>{user.deleted ? "Deleted" : "Delete"}</button>
                                                        </>}
                                                    </div>}
                                                </div>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>

                        {loading && <div className="admin-table-state">Loading users…</div>}
                        {!loading && filteredUsers.length === 0 && <div className="admin-table-state">{query || statusFilter !== "ALL" || createdFrom || createdTo ? "No users match these filters." : "No users found."}</div>}
                    </div>

                    {!loading && filteredUsers.length > 0 && (
                        <div className="admin-pagination">
                            <span>Showing {firstVisible}–{lastVisible} of {filteredUsers.length}</span>
                            <div className="admin-pagination-controls">
                                <label>
                                    Rows per page
                                    <select
                                        value={pageSize}
                                        onChange={(event) => {
                                            setPageSize(event.target.value);
                                            setPage(1);
                                        }}
                                    >
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

            {showCreate && (
                <div className="admin-modal-backdrop" onMouseDown={() => !creating && setShowCreate(false)}>
                    <section className="admin-modal" role="dialog" aria-modal="true" aria-labelledby="create-user-title" onMouseDown={(event) => event.stopPropagation()}>
                        <h2 id="create-user-title">Add user</h2>
                        <p>Create a File Drive account and assign its initial role.</p>
                        <form onSubmit={handleCreate}>
                            <label>Username<input name="username" required autoFocus autoComplete="off" /></label>
                            <label>Role<select name="role" defaultValue="USER"><option value="USER">User</option><option value="ADMIN">Admin</option></select></label>
                            <label className="admin-checkbox">
                                <input type="checkbox" checked={createOneTime} onChange={(event) => { const checked = event.target.checked; setCreateOneTime(checked); setPasswordMode(checked ? "generated" : "manual"); setCreateFormError(""); }} />
                                <span>
                                    <strong>One-time password</strong>
                                    <small>Require the user to choose their own password at first sign-in.</small>
                                </span>
                            </label>
                            {createOneTime && <div className="admin-password-mode" role="group" aria-label="Password entry method">
                                <button type="button" className={passwordMode === "generated" ? "selected" : ""} onClick={() => { setPasswordMode("generated"); setCreateFormError(""); }}>Generate password</button>
                                <button type="button" className={passwordMode === "manual" ? "selected" : ""} onClick={() => { setPasswordMode("manual"); setCreateFormError(""); }}>Enter manually</button>
                            </div>}
                            {createOneTime && passwordMode === "generated" ? <div className="admin-generated-password">
                                <span className="admin-generated-password-label">Generated temporary password</span>
                                <div className="admin-generated-password-value"><code>{generatedPassword}</code><button type="button" onClick={handleCopyPassword} aria-label="Copy generated password" title="Copy password"><ClipboardIcon /></button></div>
                                <div className="admin-generated-password-footer"><span>{passwordCopied ? "Copied to clipboard" : "Share this securely. It is only shown here."}</span><button type="button" onClick={() => { setGeneratedPassword(generateTemporaryPassword()); setPasswordCopied(false); }}><RefreshIcon />Regenerate</button></div>
                            </div> : <div className="admin-manual-password-fields">
                                <label>{createOneTime ? "Temporary password" : "Password"}<PasswordInput value={manualPassword} onChange={(event) => { setManualPassword(event.target.value); setCreateFormError(""); }} required minLength="8" autoComplete="new-password" /></label>
                                <label>Retype password<PasswordInput value={confirmPassword} onChange={(event) => { setConfirmPassword(event.target.value); setCreateFormError(""); }} required minLength="8" autoComplete="new-password" /></label>
                            </div>}
                            {createFormError && <div className="admin-create-form-error" role="alert">{createFormError}</div>}
                            <div className="admin-modal-actions">
                                <button type="button" onClick={() => setShowCreate(false)} disabled={creating}>Cancel</button>
                                <button type="submit" className="primary" disabled={creating}>
                                    <UserPlusIcon />
                                    {creating ? "Creating…" : "Create user"}
                                </button>
                            </div>
                        </form>
                    </section>
                </div>
            )}

            {editTarget && (
                <div className="admin-modal-backdrop" onMouseDown={() => !savingEdit && !savingSecurity && setEditTarget(null)}>
                    <section className="admin-modal admin-edit-user-modal" role="dialog" aria-modal="true" aria-labelledby="edit-user-title" onMouseDown={(event) => event.stopPropagation()}>
                        <div className="admin-edit-user-heading"><div><h2 id="edit-user-title">Edit {editTarget.username}</h2><p>Manage account details and sign-in security.</p></div><button type="button" onClick={() => setEditTarget(null)} aria-label="Close edit user">×</button></div>
                        <div className="admin-edit-tabs" role="tablist"><button type="button" role="tab" aria-selected={editTab === "account"} className={editTab === "account" ? "selected" : ""} onClick={() => { setEditTab("account"); setSecurityError(""); setSecurityMessage(""); }}>Account</button><button type="button" role="tab" aria-selected={editTab === "security"} className={editTab === "security" ? "selected" : ""} onClick={() => { setEditTab("security"); setSecurityError(""); setSecurityMessage(""); }}>Security</button></div>
                        {editTab === "account" ? <form onSubmit={handleEdit}>
                            <div className="admin-edit-section-heading"><strong>Account information</strong><span>Change the user’s display identity and access role.</span></div>
                            <label>Username<input name="username" required autoFocus defaultValue={editTarget.username} autoComplete="off" /></label>
                            <label>Role<select name="role" defaultValue={editTarget.role || "USER"}><option value="USER">User</option><option value="ADMIN">Admin</option></select></label>
                            {securityError && <div className="admin-create-form-error" role="alert">{securityError}</div>}
                            {securityMessage && <div className="admin-security-success" role="status">{securityMessage}</div>}
                            <div className="admin-modal-actions"><button type="button" onClick={() => setEditTarget(null)} disabled={savingEdit}>Close</button><button type="submit" className="primary" disabled={savingEdit}>{savingEdit ? "Saving…" : "Save changes"}</button></div>
                        </form> : <div className="admin-edit-security">
                            <section className="admin-security-card"><div className="admin-security-card-heading"><div><strong>Reset password</strong><span>Issue a temporary password and require a new password at next sign-in.</span></div><span className="admin-security-state">Recovery</span></div><div className="admin-generated-password-value"><code>{editTemporaryPassword}</code><button type="button" onClick={copyEditTemporaryPassword} aria-label="Copy temporary password"><ClipboardIcon /></button></div><div className="admin-generated-password-footer"><span>{editPasswordCopied ? "Copied to clipboard" : "Copy this before closing the dialog."}</span><button type="button" onClick={() => { setEditTemporaryPassword(generateTemporaryPassword()); setEditPasswordCopied(false); }}><RefreshIcon />Regenerate</button></div><p className="admin-security-warning">Resetting also signs out refresh sessions and removes login 2FA so the user can complete recovery.</p><button type="button" className="admin-security-action" onClick={handlePasswordReset} disabled={savingSecurity}>{savingSecurity ? "Resetting…" : "Reset password"}</button></section>
                            <section className="admin-security-card"><div className="admin-security-card-heading"><div><strong>Two-factor authentication</strong><span>Remove registered authenticators and security keys.</span></div><span className={`admin-security-state ${editTarget.totpEnabled || editTarget.webauthnEnabled ? "enabled" : ""}`}>{editTarget.totpEnabled || editTarget.webauthnEnabled ? "Enabled" : "Not enabled"}</span></div>{confirmRemoveTwoFactor ? <div className="admin-inline-confirm"><span>Remove all login verification methods?</span><div><button type="button" onClick={() => setConfirmRemoveTwoFactor(false)} disabled={savingSecurity}>Cancel</button><button type="button" className="danger" onClick={handleRemoveTwoFactor} disabled={savingSecurity}>{savingSecurity ? "Removing…" : "Remove 2FA"}</button></div></div> : <button type="button" className="admin-security-action danger-outline" onClick={() => setConfirmRemoveTwoFactor(true)} disabled={savingSecurity || (!editTarget.totpEnabled && !editTarget.webauthnEnabled)}>Remove 2FA</button>}</section>
                            {securityError && <div className="admin-create-form-error" role="alert">{securityError}</div>}
                            {securityMessage && <div className="admin-security-success" role="status">{securityMessage}</div>}
                            <div className="admin-modal-actions"><button type="button" onClick={() => setEditTarget(null)} disabled={savingSecurity}>Close</button></div>
                        </div>}
                    </section>
                </div>
            )}

            {suspensionTarget && (
                <div className="admin-modal-backdrop" onMouseDown={() => !savingSuspension && setSuspensionTarget(null)}>
                    <section className="admin-modal" role="alertdialog" aria-modal="true" aria-labelledby="suspend-user-title" onMouseDown={(event) => event.stopPropagation()}>
                        <h2 id="suspend-user-title">{suspensionTarget.suspended ? "Reactivate" : "Suspend"} {suspensionTarget.username}?</h2>
                        <p>{suspensionTarget.suspended ? "The user will be able to sign in and access their files again." : "The user will be blocked immediately. Their files will not be deleted."}</p>
                        {!suspensionTarget.suspended && <>
                            <fieldset className="admin-suspension-options"><legend>Suspension duration</legend>{SUSPENSION_OPTIONS.map((option) => <label key={option.value} className={suspensionDuration === option.value ? "selected" : ""}><input type="radio" name="suspensionDuration" value={option.value} checked={suspensionDuration === option.value} onChange={() => setSuspensionDuration(option.value)} /><span>{option.label}</span></label>)}</fieldset>
                            <div className="admin-suspension-expiry"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></svg><span>{suspensionExpiry(suspensionDuration)}</span></div>
                        </>}
                        <div className="admin-modal-actions"><button type="button" onClick={() => setSuspensionTarget(null)} disabled={savingSuspension}>Cancel</button><button type="button" className={suspensionTarget.suspended ? "primary" : "danger"} onClick={handleSuspension} disabled={savingSuspension}>{savingSuspension ? "Saving…" : suspensionTarget.suspended ? "Reactivate user" : "Suspend user"}</button></div>
                    </section>
                </div>
            )}

            {deleteTarget && (
                <div className="admin-modal-backdrop" onMouseDown={() => !deleting && setDeleteTarget(null)}>
                    <section className="admin-modal" role="alertdialog" aria-modal="true" aria-labelledby="delete-user-title" onMouseDown={(event) => event.stopPropagation()}>
                        <h2 id="delete-user-title">Delete {deleteTarget.username}?</h2>
                        <p>This disables the account and marks it as deleted. The user’s files, activity, and related records will be retained.</p>
                        <div className="admin-modal-actions">
                            <button type="button" onClick={() => setDeleteTarget(null)} disabled={deleting}>Cancel</button>
                            <button type="button" className="danger" onClick={handleDelete} disabled={deleting}>{deleting ? "Deleting…" : "Delete user"}</button>
                        </div>
                    </section>
                </div>
            )}

            {restoreTarget && (
                <div className="admin-modal-backdrop" onMouseDown={() => !restoring && setRestoreTarget(null)}>
                    <section className="admin-modal" role="alertdialog" aria-modal="true" aria-labelledby="restore-user-title" onMouseDown={(event) => event.stopPropagation()}>
                        <h2 id="restore-user-title">Restore {restoreTarget.username}?</h2>
                        <p>The account will become active immediately and the user will be able to sign in again. Their retained files and activity will remain available.</p>
                        <div className="admin-modal-actions">
                            <button type="button" onClick={() => setRestoreTarget(null)} disabled={restoring}>Cancel</button>
                            <button type="button" className="primary" onClick={handleRestore} disabled={restoring}>{restoring ? "Restoring…" : "Restore user"}</button>
                        </div>
                    </section>
                </div>
            )}
        </main>
    );
}
