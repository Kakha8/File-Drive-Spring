import { useEffect, useMemo, useState } from "react";
import DriveSidebar from "../components/DriveSidebar";
import NotificationMenu from "../components/NotificationMenu";
import UserMenu from "../components/UserMenu";
import { getFolder } from "../api/drive";
import {
    addToFavorites,
    getFavorites,
    removeFromFavorites,
} from "../api/favorites";

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
    File: ({ className }) => (
        <Icon className={className}>
            <path d="M6 3h8l4 4v14H6z" />
            <path d="M14 3v5h5" />
        </Icon>
    ),
    Folder: ({ className }) => (
        <Icon className={className}>
            <path d="M3 7h6l2 2h10v8.5A2.5 2.5 0 0 1 18.5 20h-13A2.5 2.5 0 0 1 3 17.5z" />
        </Icon>
    ),
    Search: ({ className }) => (
        <Icon className={className}>
            <circle cx="11" cy="11" r="7" />
            <path d="m20 20-3.5-3.5" />
        </Icon>
    ),
    Star: ({ className }) => (
        <Icon className={className}>
            <path d="M12 3.5 14.7 9l6 .9-4.35 4.25 1.05 6L12 17.3l-5.4 2.85 1.05-6L3.3 9.9l6-.9z" />
        </Icon>
    ),
    ArrowLeft: ({ className }) => (
        <Icon className={className}>
            <path d="M19 12H5" />
            <path d="m12 19-7-7 7-7" />
        </Icon>
    ),
    ArrowRight: ({ className }) => (
        <Icon className={className}>
            <path d="M5 12h14" />
            <path d="m12 5 7 7-7 7" />
        </Icon>
    ),
};

function favoriteKey(entityType, entityId) {
    return `${entityType}:${entityId}`;
}

function indexFavorites(favorites) {
    return (favorites || []).reduce((result, favorite) => {
        if (favorite?.entityType != null && favorite?.entityId != null) {
            result[favoriteKey(favorite.entityType, favorite.entityId)] = favorite;
        }
        return result;
    }, {});
}

function formatBytes(bytes) {
    if (bytes == null) return "—";
    if (bytes === 0) return "0 B";

    const units = ["B", "KB", "MB", "GB", "TB"];
    let value = Number(bytes);
    let index = 0;

    while (value >= 1024 && index < units.length - 1) {
        value /= 1024;
        index += 1;
    }

    return `${value.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}

function formatDate(value) {
    if (!value) return { date: "—", time: "" };

    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return { date: "—", time: "" };

    return {
        date: new Intl.DateTimeFormat(undefined, {
            month: "short",
            day: "numeric",
        }).format(parsed),
        time: new Intl.DateTimeFormat(undefined, {
            hour: "numeric",
            minute: "2-digit",
        }).format(parsed),
    };
}

function getTag(type) {
    if (type === "folder") return "Folder";
    if (type === "application/pdf") return "PDF";
    if (type?.startsWith("image/")) return "Image";
    if (type?.startsWith("video/")) return "Video";
    if (type?.startsWith("audio/")) return "Audio";
    return "File";
}

function tagClass(tag) {
    if (tag === "Folder") return "tag shared";
    if (tag === "PDF") return "tag final";
    if (tag === "Image") return "tag design";
    if (tag === "Video") return "tag draft";
    if (tag === "Audio") return "tag research";
    return "tag private";
}

function normalizeFavoriteRows(favorites) {
    return (favorites || []).map((favorite) => {
        const isFolder = favorite.entityType === "FOLDER";
        const edited = formatDate(favorite.createdAt);
        const type = isFolder ? "folder" : favorite.objectType || "file";

        return {
            id: `favorite-${favorite.id}`,
            entityId: favorite.entityId,
            entityType: favorite.entityType,
            name:
                favorite.name ||
                (isFolder
                    ? `Folder #${favorite.entityId}`
                    : `File #${favorite.entityId}`),
            type,
            owner: favorite.ownerUsername || "You",
            initials: (favorite.ownerUsername || "ME").slice(0, 2).toUpperCase(),
            tag: getTag(type),
            date: edited.date,
            time: edited.time,
            size: isFolder ? "—" : formatBytes(favorite.size),
            isFolder,
        };
    });
}

function normalizeFolderRows(folder, favoritesByKey) {
    if (!folder) return [];

    const folders = (folder.folders || []).map((item) => {
        const edited = formatDate(item.lastModifiedDate);
        return {
            id: `folder-${item.id}`,
            entityId: item.id,
            entityType: "FOLDER",
            name: item.name || "Untitled folder",
            type: "folder",
            owner: item.ownerUsername || "You",
            initials: (item.ownerUsername || "ME").slice(0, 2).toUpperCase(),
            tag: "Folder",
            date: edited.date,
            time: edited.time,
            size: "—",
            isFolder: true,
            favorite: favoritesByKey[favoriteKey("FOLDER", item.id)] || null,
        };
    });

    const files = (folder.files || [])
        .filter((item) => !item.deleted)
        .map((item) => {
            const edited = formatDate(item.lastModifiedDate || item.creationDate);
            const type = item.objectType || "file";
            return {
                id: `file-${item.id}`,
                entityId: item.id,
                entityType: "FILE",
                name: item.fileName || "Untitled file",
                type,
                owner: item.ownerUsername || "You",
                initials: (item.ownerUsername || "ME").slice(0, 2).toUpperCase(),
                tag: getTag(type),
                date: edited.date,
                time: edited.time,
                size: formatBytes(item.size),
                isFolder: false,
                favorite: favoritesByKey[favoriteKey("FILE", item.id)] || null,
            };
        });

    return [...folders, ...files];
}

export default function Favorites({
                                      sidebarOpen,
                                      onToggleSidebar,
                                      onLogout,
                                  }) {
    const [favorites, setFavorites] = useState([]);
    const [currentFolder, setCurrentFolder] = useState(null);
    const [path, setPath] = useState([{ id: null, name: "Favorites" }]);
    const [forwardStack, setForwardStack] = useState([]);
    const [query, setQuery] = useState("");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [pendingKeys, setPendingKeys] = useState(() => new Set());

    const atRoot = path.length === 1;
    const favoritesByKey = useMemo(
        () => indexFavorites(favorites),
        [favorites]
    );

    useEffect(() => {
        let cancelled = false;

        async function load() {
            try {
                setLoading(true);
                setError("");
                const result = await getFavorites();

                if (!cancelled) {
                    setFavorites(Array.isArray(result) ? result : []);
                }
            } catch (err) {
                if (!cancelled) {
                    setError(err.message || "Failed to load favorites");
                }
            } finally {
                if (!cancelled) setLoading(false);
            }
        }

        load();
        return () => {
            cancelled = true;
        };
    }, []);

    const rows = useMemo(
        () =>
            atRoot
                ? normalizeFavoriteRows(favorites)
                : normalizeFolderRows(currentFolder, favoritesByKey),
        [atRoot, currentFolder, favorites, favoritesByKey]
    );

    const visibleRows = useMemo(() => {
        const q = query.trim().toLowerCase();
        if (!q) return rows;

        return rows.filter((row) =>
            `${row.name} ${row.owner} ${row.type} ${row.tag}`
                .toLowerCase()
                .includes(q)
        );
    }, [rows, query]);

    function setPending(key, pending) {
        setPendingKeys((current) => {
            const next = new Set(current);
            pending ? next.add(key) : next.delete(key);
            return next;
        });
    }

    async function refreshFavorites() {
        const result = await getFavorites();
        setFavorites(Array.isArray(result) ? result : []);
    }

    async function toggleFavorite(row) {
        const key = favoriteKey(row.entityType, row.entityId);
        if (pendingKeys.has(key)) return;

        const existing = favoritesByKey[key];
        const previous = favorites;

        setError("");
        setPending(key, true);

        try {
            if (existing) {
                setFavorites((current) =>
                    current.filter((item) => item.id !== existing.id)
                );
                await removeFromFavorites(existing.id);
            } else {
                await addToFavorites(row.entityType, row.entityId);
                await refreshFavorites();
            }
        } catch (err) {
            setFavorites(previous);
            setError(err.message || "Failed to update favorite");
        } finally {
            setPending(key, false);
        }
    }

    async function openFolder(row) {
        if (!row?.isFolder) return;

        try {
            setLoading(true);
            setError("");
            const folder = await getFolder(row.entityId);

            setCurrentFolder(folder);
            setPath((current) => [
                ...current,
                { id: row.entityId, name: row.name || "Folder" },
            ]);
            setForwardStack([]);
            setQuery("");
        } catch (err) {
            setError(err.message || "Failed to open folder");
        } finally {
            setLoading(false);
        }
    }

    async function goToPath(index) {
        const target = path[index];
        if (!target) return;

        try {
            setLoading(true);
            setError("");

            setCurrentFolder(index === 0 ? null : await getFolder(target.id));
            setPath((current) => current.slice(0, index + 1));
            setForwardStack([]);
            setQuery("");
        } catch (err) {
            setError(err.message || "Failed to open folder");
        } finally {
            setLoading(false);
        }
    }

    async function goBack() {
        if (path.length <= 1) return;

        const leaving = path[path.length - 1];
        const targetIndex = path.length - 2;
        const target = path[targetIndex];

        try {
            setLoading(true);
            setError("");

            setCurrentFolder(
                targetIndex === 0 ? null : await getFolder(target.id)
            );
            setPath((current) => current.slice(0, targetIndex + 1));
            setForwardStack((current) => [leaving, ...current]);
            setQuery("");
        } catch (err) {
            setError(err.message || "Failed to go back");
        } finally {
            setLoading(false);
        }
    }

    async function goForward() {
        if (forwardStack.length === 0) return;

        const next = forwardStack[0];

        try {
            setLoading(true);
            setError("");

            setCurrentFolder(await getFolder(next.id));
            setPath((current) => [...current, next]);
            setForwardStack((current) => current.slice(1));
            setQuery("");
        } catch (err) {
            setError(err.message || "Failed to go forward");
        } finally {
            setLoading(false);
        }
    }

    return (
        <div className="drive-page">
            <style>{`
                .favorites-header-location {
                    display: flex;
                    align-items: center;
                    min-width: 0;
                }

                .favorites-nav-controls {
                    display: flex;
                    gap: 4px;
                    margin-right: 10px;
                }

                .favorites-nav-button,
                .favorite-browser-star {
                    width: 34px;
                    height: 34px;
                    border: 0;
                    border-radius: 8px;
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    background: transparent;
                    cursor: pointer;
                }

                .favorites-nav-button {
                    color: #9ca3af;
                }

                .favorites-nav-button:hover:not(:disabled) {
                    background: rgba(148, 163, 184, 0.12);
                    color: #fff;
                }

                .favorites-nav-button:disabled {
                    opacity: 0.3;
                    cursor: default;
                }

                .favorites-breadcrumb {
                    border: 0;
                    background: transparent;
                    color: #9099a8;
                    cursor: pointer;
                    padding: 4px 2px;
                }

                .favorites-breadcrumb:hover,
                .favorites-breadcrumb.current {
                    color: #fff;
                }

                .favorite-browser-star {
                    color: #9ca3af;
                    opacity: 0;
                    transition:
                        opacity 140ms ease,
                        color 140ms ease,
                        background-color 140ms ease;
                }

                .file-row:hover .favorite-browser-star,
                .favorite-browser-star.is-favorite,
                .favorite-browser-star:focus-visible {
                    opacity: 1;
                }

                .favorite-browser-star:hover {
                    color: var(--old-accent, #3b82f6);
                    background: var(--old-accent-soft, rgba(59, 130, 246, 0.18));
                }

                .favorite-browser-star:disabled {
                    opacity: 0.45;
                    cursor: wait;
                }

                .favorite-browser-star-icon {
                    width: 18px;
                    height: 18px;
                    fill: transparent;
                }

                .favorite-browser-star.is-favorite {
                    color: var(--old-accent, #3b82f6);
                }

                .favorite-browser-star.is-favorite
                .favorite-browser-star-icon {
                    fill: currentColor;
                }

                .favorites-folder-row {
                    cursor: pointer;
                }

                .favorites-folder-row:hover .name-cell strong {
                     text-decoration: none;
                }
            `}</style>

            <DriveSidebar
                active="favorites"
                sidebarOpen={sidebarOpen}
                onToggleSidebar={onToggleSidebar}
                username="My workspace"
                locationLabel="Favorites"
                onLogoutComplete={onLogout}
            />

            <main className="drive-main">
                <header className="drive-header">
                    <div className="favorites-header-location">
                        <div className="favorites-nav-controls">
                            <button
                                type="button"
                                className="favorites-nav-button"
                                onClick={goBack}
                                disabled={path.length <= 1 || loading}
                                title="Go back"
                                aria-label="Go back"
                            >
                                <Icons.ArrowLeft className="svg-icon" />
                            </button>

                            <button
                                type="button"
                                className="favorites-nav-button"
                                onClick={goForward}
                                disabled={forwardStack.length === 0 || loading}
                                title="Go forward"
                                aria-label="Go forward"
                            >
                                <Icons.ArrowRight className="svg-icon" />
                            </button>
                        </div>

                        <div className="breadcrumbs">
                            {path.map((part, index) => (
                                <span
                                    key={`${part.id ?? "favorites"}-${index}`}
                                    className="breadcrumb-part"
                                >
                                    {index > 0 && (
                                        <span className="breadcrumb-slash">/</span>
                                    )}

                                    <button
                                        type="button"
                                        className={`favorites-breadcrumb ${
                                            index === path.length - 1
                                                ? "current"
                                                : ""
                                        }`}
                                        onClick={() => goToPath(index)}
                                        disabled={loading}
                                    >
                                        {part.name}
                                    </button>
                                </span>
                            ))}
                        </div>
                    </div>

                    <div className="drive-header-actions">
                        <NotificationMenu onLogout={onLogout} />
                        <UserMenu onLogout={onLogout} />
                    </div>
                </header>

                <div className="toolbar">
                    <div className="search-box">
                        <Icons.Search className="search-icon" />

                        <input
                            value={query}
                            onChange={(event) => setQuery(event.target.value)}
                            placeholder={
                                atRoot
                                    ? "Search favorites"
                                    : "Search this folder"
                            }
                        />

                        <kbd>⌘ K</kbd>
                    </div>
                </div>

                <div className="content-layout">
                    <section className="file-table-wrap">
                        <div className="file-table">
                            <div className="file-row table-head">
                                <div>Name</div>
                                <div>Owner</div>
                                <div>Tags</div>
                                <div>{atRoot ? "Added" : "Last edited"}</div>
                                <div>Size</div>
                                <div />
                            </div>

                            {loading && (
                                <div className="empty-state">Loading...</div>
                            )}

                            {!loading && error && (
                                <div className="empty-state">{error}</div>
                            )}

                            {!loading &&
                                !error &&
                                visibleRows.map((row) => {
                                    const ItemIcon = row.isFolder
                                        ? Icons.Folder
                                        : Icons.File;
                                    const key = favoriteKey(
                                        row.entityType,
                                        row.entityId
                                    );
                                    const isFavorite = Boolean(
                                        favoritesByKey[key]
                                    );
                                    const pending = pendingKeys.has(key);

                                    return (
                                        <div
                                            key={row.id}
                                            className={`file-row ${
                                                row.isFolder
                                                    ? "favorites-folder-row"
                                                    : ""
                                            }`}
                                            onDoubleClick={() => openFolder(row)}
                                            role={
                                                row.isFolder
                                                    ? "button"
                                                    : undefined
                                            }
                                            tabIndex={
                                                row.isFolder ? 0 : undefined
                                            }
                                            onKeyDown={(event) => {
                                                if (
                                                    !row.isFolder ||
                                                    (event.key !== "Enter" &&
                                                        event.key !== " ")
                                                ) {
                                                    return;
                                                }

                                                event.preventDefault();
                                                openFolder(row);
                                            }}
                                        >
                                            <div className="name-cell">
                                                <span className="file-icon">
                                                    <ItemIcon className="svg-icon" />
                                                </span>

                                                <span>
                                                    <strong>{row.name}</strong>
                                                    <small>{row.type}</small>
                                                </span>
                                            </div>

                                            <div className="owner-cell">
                                                <span className="avatar">
                                                    {row.initials}
                                                </span>
                                                <span>{row.owner}</span>
                                            </div>

                                            <div className="tags-cell">
                                                <span className={tagClass(row.tag)}>
                                                    {row.tag}
                                                </span>
                                            </div>

                                            <div>
                                                {row.date}
                                                {row.time && (
                                                    <small>{row.time}</small>
                                                )}
                                            </div>

                                            <div className="size-cell">
                                                {row.size}
                                            </div>

                                            <div
                                                className="row-actions"
                                                onClick={(event) =>
                                                    event.stopPropagation()
                                                }
                                                onDoubleClick={(event) =>
                                                    event.stopPropagation()
                                                }
                                            >
                                                <button
                                                    type="button"
                                                    className={`favorite-browser-star ${
                                                        isFavorite
                                                            ? "is-favorite"
                                                            : ""
                                                    }`}
                                                    title={
                                                        isFavorite
                                                            ? "Remove from favorites"
                                                            : "Add to favorites"
                                                    }
                                                    aria-label={
                                                        isFavorite
                                                            ? `Remove ${row.name} from favorites`
                                                            : `Add ${row.name} to favorites`
                                                    }
                                                    aria-pressed={isFavorite}
                                                    disabled={pending}
                                                    onClick={() =>
                                                        toggleFavorite(row)
                                                    }
                                                >
                                                    <Icons.Star className="favorite-browser-star-icon" />
                                                </button>
                                            </div>
                                        </div>
                                    );
                                })}

                            {!loading &&
                                !error &&
                                visibleRows.length === 0 && (
                                    <div className="empty-state">
                                        {query
                                            ? "No items match your search."
                                            : atRoot
                                                ? "You have no favorites yet."
                                                : "This folder is empty."}
                                    </div>
                                )}
                        </div>

                        {!loading && !error && (
                            <div className="item-count">
                                {visibleRows.length}{" "}
                                {visibleRows.length === 1 ? "item" : "items"}
                            </div>
                        )}
                    </section>
                </div>
            </main>
        </div>
    );
}