import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getCurrentUsername } from "../api/auth";
import {
    createAdminUser,
    deleteAdminUser,
    getAdminUsers,
} from "../api/users";
import DriveSidebar from "../components/DriveSidebar";
import PasswordInput from "../components/PasswordInput";

function UserPlusIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M15 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
            <circle cx="8.5" cy="7" r="4" />
            <path d="M19 8v6M16 11h6" />
        </svg>
    );
}

export default function AdminUsers({ onLogout, sidebarOpen, onToggleSidebar }) {
    const navigate = useNavigate();
    const currentUsername = getCurrentUsername();
    const [users, setUsers] = useState([]);
    const [query, setQuery] = useState("");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [showCreate, setShowCreate] = useState(false);
    const [creating, setCreating] = useState(false);
    const [deleteTarget, setDeleteTarget] = useState(null);
    const [deleting, setDeleting] = useState(false);
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

    const filteredUsers = useMemo(() => {
        const normalizedQuery = query.trim().toLowerCase();
        if (!normalizedQuery) return users;

        return users.filter((user) =>
            user.username?.toLowerCase().includes(normalizedQuery)
        );
    }, [query, users]);

    const numericPageSize = pageSize === "all" ? Math.max(filteredUsers.length, 1) : Number(pageSize);
    const totalPages = Math.max(1, Math.ceil(filteredUsers.length / numericPageSize));
    const currentPage = Math.min(page, totalPages);
    const visibleUsers = pageSize === "all"
        ? filteredUsers
        : filteredUsers.slice((currentPage - 1) * numericPageSize, currentPage * numericPageSize);
    const firstVisible = filteredUsers.length === 0 ? 0 : (currentPage - 1) * numericPageSize + 1;
    const lastVisible = Math.min(currentPage * numericPageSize, filteredUsers.length);

    async function handleCreate(event) {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        const username = String(form.get("username") || "").trim();
        const password = String(form.get("password") || "");
        const role = String(form.get("role") || "USER");
        const passwordChangeRequired = form.get("passwordChangeRequired") === "on";

        setCreating(true);
        setError("");

        try {
            await createAdminUser({ username, password, role, passwordChangeRequired });
            setShowCreate(false);
            await loadUsers();
        } catch (requestError) {
            setError(requestError.message || "Failed to create user");
        } finally {
            setCreating(false);
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
                    <button type="button" className="admin-primary-button" onClick={() => setShowCreate(true)}>
                        <UserPlusIcon />
                        Add user
                    </button>
                </header>

                <div className="admin-users-content">
                    <div className="admin-users-toolbar">
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
                        <span>{users.length} {users.length === 1 ? "user" : "users"}</span>
                    </div>

                    {error && <div className="admin-message error" role="alert">{error}</div>}

                    <div className="admin-users-table-wrap">
                        <table className="admin-users-table">
                            <thead>
                                <tr><th>User</th><th>Role</th><th className="admin-actions-column">Actions</th></tr>
                            </thead>
                            <tbody>
                                {visibleUsers.map((user) => {
                                    const isCurrentUser = user.username === currentUsername;
                                    return (
                                        <tr key={user.id}>
                                            <td>
                                                <div className="admin-user-cell">
                                                    <span className="admin-user-avatar">{user.username?.slice(0, 1).toUpperCase()}</span>
                                                    <span><strong>{user.username}</strong>{isCurrentUser && <small>You</small>}</span>
                                                </div>
                                            </td>
                                            <td><span className={`admin-role-badge ${user.role === "ADMIN" ? "admin" : ""}`}>{user.role || "USER"}</span></td>
                                            <td className="admin-actions-column">
                                                <button
                                                    type="button"
                                                    className="admin-delete-button"
                                                    onClick={() => setDeleteTarget(user)}
                                                    disabled={isCurrentUser}
                                                    title={isCurrentUser ? "You cannot delete your own account" : `Delete ${user.username}`}
                                                >
                                                    Delete
                                                </button>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>

                        {loading && <div className="admin-table-state">Loading users…</div>}
                        {!loading && filteredUsers.length === 0 && <div className="admin-table-state">{query ? "No users match your search." : "No users found."}</div>}
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
                            <label>Temporary password<PasswordInput name="password" required minLength="8" autoComplete="new-password" /></label>
                            <label>Role<select name="role" defaultValue="USER"><option value="USER">User</option><option value="ADMIN">Admin</option></select></label>
                            <label className="admin-checkbox">
                                <input name="passwordChangeRequired" type="checkbox" defaultChecked />
                                <span>
                                    <strong>One-time password</strong>
                                    <small>Require the user to choose their own password at first sign-in.</small>
                                </span>
                            </label>
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

            {deleteTarget && (
                <div className="admin-modal-backdrop" onMouseDown={() => !deleting && setDeleteTarget(null)}>
                    <section className="admin-modal" role="alertdialog" aria-modal="true" aria-labelledby="delete-user-title" onMouseDown={(event) => event.stopPropagation()}>
                        <h2 id="delete-user-title">Delete {deleteTarget.username}?</h2>
                        <p>This permanently deletes the account. The operation may be rejected while the user still owns related data.</p>
                        <div className="admin-modal-actions">
                            <button type="button" onClick={() => setDeleteTarget(null)} disabled={deleting}>Cancel</button>
                            <button type="button" className="danger" onClick={handleDelete} disabled={deleting}>{deleting ? "Deleting…" : "Delete user"}</button>
                        </div>
                    </section>
                </div>
            )}
        </main>
    );
}
