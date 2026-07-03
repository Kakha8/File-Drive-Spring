import { useEffect, useMemo, useRef, useState } from "react";
import { apiFetch } from "../api/http";
import "./ShareModal.css";

async function readApiError(response, fallbackMessage) {
    try {
        const data = await response.json();
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

export default function ShareModal({
                                       open,
                                       target,
                                       loading = false,
                                       onClose,
                                       onSubmit,
                                   }) {
    const [query, setQuery] = useState("");
    const [results, setResults] = useState([]);
    const [selectedUsers, setSelectedUsers] = useState([]);
    const [error, setError] = useState("");
    const [searching, setSearching] = useState(false);
    const [activeIndex, setActiveIndex] = useState(0);

    const inputRef = useRef(null);

    function getUsername(user) {
        return String(user?.username || user?.name || "").trim();
    }

    const selectedUsernames = useMemo(
        () => new Set(selectedUsers.map((user) => getUsername(user))),
        [selectedUsers]
    );

    const visibleResults = results.filter(
        (user) => !selectedUsernames.has(getUsername(user))
    );

    useEffect(() => {
        if (!open) return;

        setQuery("");
        setResults([]);
        setSelectedUsers([]);
        setError("");
        setSearching(false);
        setActiveIndex(0);

        setTimeout(() => {
            inputRef.current?.focus();
        }, 0);

        function handleEscape(event) {
            if (event.key === "Escape" && !loading) {
                onClose?.();
            }
        }

        window.addEventListener("keydown", handleEscape);

        return () => {
            window.removeEventListener("keydown", handleEscape);
        };
    }, [open, onClose, loading]);

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

            return [
                ...current,
                {
                    ...user,
                    username,
                    role: "VIEWER",
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

        if (selectedUsers.length === 0) {
            setError("Select at least one user");
            inputRef.current?.focus();
            return;
        }

        const shares = selectedUsers.map((user) => ({
            userId: user.id,
            username: getUsername(user),
            role: user.role || "VIEWER",
        }));

        setError("");

        await onSubmit?.({
            resourceType: target.resourceType,
            resourceId: target.resourceId,
            shares,
        });
    }

    return (
        <div
            className="share-modal-backdrop"
            onMouseDown={() => {
                if (!loading) {
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
                        disabled={loading}
                        aria-label="Close share modal"
                    >
                        ×
                    </button>
                </div>

                <div className="share-modal-body">
                    <label className="share-field">
                        <span>People</span>

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
                                disabled={loading}
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

                    {selectedUsers.length > 0 && (
                        <div className="share-selected-users">
                            <p className="share-selected-title">
                                People to share with
                            </p>

                            {selectedUsers.map((user) => {
                                const username = getUsername(user);

                                return (
                                    <div
                                        key={user.id ?? username}
                                        className="share-selected-user"
                                    >
                                        <div className="share-selected-user-main">
                                            <span className="share-user-avatar">
                                                {username
                                                    .slice(0, 1)
                                                    .toUpperCase()}
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
                                                disabled={loading}
                                            >
                                                <option value="VIEWER">
                                                    Viewer
                                                </option>
                                                <option value="EDITOR">
                                                    Editor
                                                </option>
                                            </select>

                                            <button
                                                type="button"
                                                className="share-remove-user"
                                                onClick={() =>
                                                    removeUser(username)
                                                }
                                                disabled={loading}
                                                aria-label={`Remove ${username}`}
                                            >
                                                ×
                                            </button>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}

                    <div className="share-role-help">
                        <p>
                            Viewers can view and download. Editors can rename,
                            upload into shared folders, and copy into shared
                            folders. Owners stay unchanged.
                        </p>
                    </div>

                    {error && <p className="share-modal-error">{error}</p>}
                </div>

                <div className="share-modal-actions">
                    <button
                        type="button"
                        className="share-btn share-btn-secondary"
                        onClick={onClose}
                        disabled={loading}
                    >
                        Cancel
                    </button>

                    <button
                        type="submit"
                        className="share-btn share-btn-primary"
                        disabled={loading}
                    >
                        {loading
                            ? "Sharing..."
                            : selectedUsers.length > 1
                                ? `Share with ${selectedUsers.length} people`
                                : "Share"}
                    </button>
                </div>
            </form>
        </div>
    );
}