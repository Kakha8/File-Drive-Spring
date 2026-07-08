import { useEffect, useMemo, useRef, useState } from "react";
import { apiFetch } from "../api/http";
import { getResourceShares } from "../api/sharing";
import "./ShareModal.css";

async function readApiError(response, fallbackMessage) {
    try {
        const data = await response.clone().json();
        return data.message || data.error || fallbackMessage;
    } catch {
        try {
            const text = await response.text();
            return text || fallbackMessage;
        } catch {
            return fallbackMessage;
        }
    }
}

async function searchUsers(query) {
    const response = await apiFetch(
        `/api/users/search?q=${encodeURIComponent(query)}`
    );

    if (!response.ok) {
        throw new Error(await readApiError(response, "Failed to search users"));
    }

    return response.json();
}

function getUsername(user) {
    return String(
        user?.username ||
        user?.sharedWithUsername ||
        user?.name ||
        ""
    ).trim();
}

export default function ShareModal({
                                       open,
                                       target,
                                       loading = false,
                                       successMessage = "",
                                       onClose,
                                       onSubmit,
                                   }) {
    const [query, setQuery] = useState("");
    const [results, setResults] = useState([]);
    const [selectedUsers, setSelectedUsers] = useState([]);
    const [originalShares, setOriginalShares] = useState([]);
    const [error, setError] = useState("");
    const [searching, setSearching] = useState(false);
    const [existingLoading, setExistingLoading] = useState(false);
    const [activeIndex, setActiveIndex] = useState(0);

    const inputRef = useRef(null);

    const selectedUsernames = useMemo(
        () => new Set(selectedUsers.map((user) => getUsername(user))),
        [selectedUsers]
    );

    const visibleResults = results.filter(
        (user) => !selectedUsernames.has(getUsername(user))
    );

    useEffect(() => {
        if (!open || !target) return;

        let cancelled = false;

        async function loadExistingShares() {
            try {
                setQuery("");
                setResults([]);
                setSelectedUsers([]);
                setOriginalShares([]);
                setError("");
                setSearching(false);
                setExistingLoading(true);
                setActiveIndex(0);

                const shares = await getResourceShares(
                    target.resourceType,
                    target.resourceId
                );

                if (cancelled) return;

                const normalizedShares = (shares || [])
                    .map((share) => {
                        const username = getUsername(share);

                        if (!username) return null;

                        return {
                            id: `share-${share.shareId}`,
                            shareId: share.shareId,
                            username,
                            role: share.role || "VIEWER",
                            existing: true,
                        };
                    })
                    .filter(Boolean);

                setOriginalShares(normalizedShares);
                setSelectedUsers(normalizedShares);

                setTimeout(() => {
                    inputRef.current?.focus();
                }, 0);
            } catch (err) {
                if (cancelled) return;
                setError(err.message || "Failed to load current shares");
            } finally {
                if (!cancelled) {
                    setExistingLoading(false);
                }
            }
        }

        loadExistingShares();

        function handleEscape(event) {
            if (event.key === "Escape" && !loading) {
                onClose?.();
            }
        }

        window.addEventListener("keydown", handleEscape);

        return () => {
            cancelled = true;
            window.removeEventListener("keydown", handleEscape);
        };
    }, [open, target, onClose, loading]);

    useEffect(() => {
        if (!open) return;

        const cleanQuery = query.trim();

        if (cleanQuery.length < 1) {
            setResults([]);
            setSearching(false);
            setActiveIndex(0);
            return;
        }

        let cancelled = false;

        async function runSearch() {
            try {
                setSearching(true);
                setError("");

                const users = await searchUsers(cleanQuery);

                if (cancelled) return;

                setResults(Array.isArray(users) ? users : []);
                setActiveIndex(0);
            } catch (err) {
                if (cancelled) return;

                setResults([]);
                setError(err.message || "Failed to search users");
            } finally {
                if (!cancelled) {
                    setSearching(false);
                }
            }
        }

        const timer = setTimeout(runSearch, 250);

        return () => {
            cancelled = true;
            clearTimeout(timer);
        };
    }, [query, open]);

    if (!open || !target) {
        return null;
    }

    const busy = loading || existingLoading;

    function addUser(user) {
        const username = getUsername(user);

        if (!username) return;

        setSelectedUsers((current) => {
            const alreadySelected = current.some(
                (selected) => getUsername(selected) === username
            );

            if (alreadySelected) {
                return current;
            }

            const originalShare = originalShares.find(
                (share) => getUsername(share) === username
            );

            return [
                ...current,
                {
                    ...user,
                    ...originalShare,
                    id: originalShare?.id || user.id || username,
                    shareId: originalShare?.shareId,
                    username,
                    role: originalShare?.role || user.role || "VIEWER",
                    existing: Boolean(originalShare),
                },
            ];
        });

        setQuery("");
        setResults([]);
        setActiveIndex(0);
        setError("");

        setTimeout(() => {
            inputRef.current?.focus();
        }, 0);
    }

    function removeUser(username) {
        setSelectedUsers((current) =>
            current.filter((user) => getUsername(user) !== username)
        );
    }

    function updateUserRole(username, role) {
        setSelectedUsers((current) =>
            current.map((user) => {
                if (getUsername(user) !== username) {
                    return user;
                }

                return {
                    ...user,
                    role,
                };
            })
        );
    }

    function handleInputKeyDown(event) {
        if (event.key === "ArrowDown") {
            event.preventDefault();

            setActiveIndex((current) => {
                if (visibleResults.length === 0) return 0;
                return Math.min(current + 1, visibleResults.length - 1);
            });

            return;
        }

        if (event.key === "ArrowUp") {
            event.preventDefault();
            setActiveIndex((current) => Math.max(current - 1, 0));
            return;
        }

        if (event.key === "Enter") {
            if (visibleResults.length > 0) {
                event.preventDefault();
                addUser(visibleResults[activeIndex] || visibleResults[0]);
            }

            return;
        }

        if (
            event.key === "Backspace" &&
            query.length === 0 &&
            selectedUsers.length > 0
        ) {
            removeUser(getUsername(selectedUsers[selectedUsers.length - 1]));
        }
    }

    async function handleSubmit(event) {
        event.preventDefault();

        const shares = selectedUsers
            .map((user) => ({
                userId: user.id,
                username: getUsername(user),
                role: user.role || "VIEWER",
            }))
            .filter((share) => share.username);

        const selectedNames = new Set(
            shares.map((share) => share.username)
        );

        const removedShareIds = originalShares
            .filter((share) => !selectedNames.has(getUsername(share)))
            .map((share) => share.shareId)
            .filter(Boolean);

        if (shares.length === 0 && removedShareIds.length === 0) {
            setError("No sharing changes to save");
            inputRef.current?.focus();
            return;
        }

        setError("");

        await onSubmit?.({
            resourceType: target.resourceType,
            resourceId: target.resourceId,
            shares,
            removedShareIds,
        });
    }

    return (
        <div
            className="share-modal-backdrop"
            onMouseDown={() => {
                if (!busy) {
                    onClose?.();
                }
            }}
        >
            <form
                className="share-modal"
                onSubmit={handleSubmit}
                onMouseDown={(event) => event.stopPropagation()}
            >
                <div className="share-modal-header">
                    <div>
                        <p className="share-modal-eyebrow">Share</p>
                        <h2>{target.name}</h2>
                    </div>

                    <button
                        type="button"
                        className="share-modal-close"
                        onClick={onClose}
                        disabled={busy}
                        aria-label="Close share modal"
                    >
                        ×
                    </button>
                </div>

                <div className="share-modal-body">
                    <label className="share-field">
                        <span>Add people</span>

                        <div
                            className="share-user-picker"
                            onClick={() => inputRef.current?.focus()}
                        >
                            <input
                                ref={inputRef}
                                value={query}
                                onChange={(event) =>
                                    setQuery(event.target.value)
                                }
                                onKeyDown={handleInputKeyDown}
                                placeholder="Search users..."
                                disabled={busy}
                            />
                        </div>

                        {(query.trim().length > 0 || searching) && (
                            <div className="share-search-results">
                                {searching && (
                                    <div className="share-search-status">
                                        Searching...
                                    </div>
                                )}

                                {!searching &&
                                    visibleResults.map((user, index) => {
                                        const username = getUsername(user);

                                        return (
                                            <button
                                                key={user.id ?? username}
                                                type="button"
                                                className={`share-search-result ${
                                                    index === activeIndex
                                                        ? "active"
                                                        : ""
                                                }`}
                                                onClick={() => addUser(user)}
                                            >
                                                <span className="share-user-avatar">
                                                    {username
                                                        .slice(0, 1)
                                                        .toUpperCase()}
                                                </span>

                                                <span>
                                                    <strong>{username}</strong>
                                                </span>
                                            </button>
                                        );
                                    })}

                                {!searching &&
                                    query.trim().length > 0 &&
                                    visibleResults.length === 0 && (
                                        <div className="share-search-status">
                                            No users found
                                        </div>
                                    )}
                            </div>
                        )}
                    </label>

                    <div className="share-selected-section">
                        <p className="share-selected-title">
                            People with access

                            {selectedUsers.length > 0 && (
                                <span className="share-selected-count">
                {selectedUsers.length}
            </span>
                            )}
                        </p>

                        <div className="share-selected-users">
                            {existingLoading ? (
                                <div className="share-search-status">
                                    Loading current shares...
                                </div>
                            ) : selectedUsers.length === 0 ? (
                                <div className="share-search-status">
                                    Only you have access.
                                </div>
                            ) : (
                                selectedUsers.map((user) => {
                                    const username = getUsername(user);

                                    return (
                                        <div
                                            key={user.shareId || user.id || username}
                                            className="share-selected-user"
                                        >
                                            <div className="share-selected-user-main">
                            <span className="share-user-avatar">
                                {username.slice(0, 1).toUpperCase()}
                            </span>

                                                <span className="share-selected-username">
                                {username}
                            </span>
                                            </div>

                                            <div className="share-selected-actions">
                                                <select
                                                    className="share-selected-role"
                                                    value={user.role || "VIEWER"}
                                                    onChange={(event) =>
                                                        updateUserRole(
                                                            username,
                                                            event.target.value
                                                        )
                                                    }
                                                    disabled={busy}
                                                >
                                                    <option value="VIEWER">Viewer</option>
                                                    <option value="EDITOR">Editor</option>
                                                </select>

                                                <button
                                                    type="button"
                                                    className="share-remove-user"
                                                    onClick={() => removeUser(username)}
                                                    disabled={busy}
                                                    aria-label={`Remove ${username}`}
                                                >
                                                    ×
                                                </button>
                                            </div>
                                        </div>
                                    );
                                })
                            )}
                        </div>
                    </div>

                    <div className="share-role-help">
                        <p>
                            Viewers can view and download. Editors can rename,
                            upload into shared folders, and copy into shared
                            folders. Owners stay unchanged.
                        </p>
                    </div>

                    {error && <p className="share-modal-error">{error}</p>}

                    {successMessage && (
                        <p className="share-modal-success">{successMessage}</p>
                    )}

                    <div
                        style={{
                            display: "flex",
                            justifyContent: "flex-end",
                            alignItems: "center",
                            gap: "12px",
                            marginTop: "22px",
                            paddingTop: "18px",
                            borderTop: "1px solid rgba(148, 163, 184, 0.18)",
                        }}
                    >
                        <button
                            type="button"
                            onClick={onClose}
                            disabled={busy}
                            style={{
                                minWidth: "96px",
                                border: "none",
                                borderRadius: "12px",
                                padding: "10px 16px",
                                background: "#1f2937",
                                color: "#f3f4f6",
                                fontSize: "14px",
                                fontWeight: 700,
                                cursor: busy ? "not-allowed" : "pointer",
                                opacity: busy ? 0.55 : 1,
                            }}
                        >
                            Cancel
                        </button>

                        <button
                            type="submit"
                            disabled={busy || Boolean(successMessage)}
                            style={{
                                minWidth: "96px",
                                border: "none",
                                borderRadius: "12px",
                                padding: "10px 16px",
                                background:
                                    busy || Boolean(successMessage)
                                        ? "#2563eb"
                                        : "#3b82f6",
                                color: "#ffffff",
                                fontSize: "14px",
                                fontWeight: 700,
                                cursor:
                                    busy || Boolean(successMessage)
                                        ? "not-allowed"
                                        : "pointer",
                                opacity:
                                    busy || Boolean(successMessage)
                                        ? 0.65
                                        : 1,
                                display: "inline-flex",
                                alignItems: "center",
                                justifyContent: "center",
                            }}
                        >
                            {successMessage
                                ? "Saved"
                                : loading
                                    ? "Saving..."
                                    : existingLoading
                                        ? "Loading..."
                                        : "Save"}
                        </button>
                    </div>
                </div>
            </form>
        </div>
    );
}