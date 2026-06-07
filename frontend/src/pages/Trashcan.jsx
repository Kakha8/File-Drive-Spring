import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getTrashcan } from "../api/drive";
import { logout as apiLogout } from "../api/auth";
import "../App.css";

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
    Folder: ({ className }) => (
        <Icon className={className}>
            <path d="M3 7h6l2 2h10v8.5A2.5 2.5 0 0 1 18.5 20h-13A2.5 2.5 0 0 1 3 17.5z" />
        </Icon>
    ),
    Image: ({ className }) => (
        <Icon className={className}>
            <rect x="3" y="5" width="18" height="14" rx="3" />
            <path d="m8 14 2.5-3 3.5 4 2-2 3 4" />
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
    Search: ({ className }) => (
        <Icon className={className}>
            <circle cx="11" cy="11" r="7" />
            <path d="m20 20-3.5-3.5" />
        </Icon>
    ),
    Refresh: ({ className }) => (
        <Icon className={className}>
            <path d="M20 6v5h-5" />
            <path d="M4 18v-5h5" />
            <path d="M18.5 9A7 7 0 0 0 6.2 6.2L4 8.4" />
            <path d="M5.5 15A7 7 0 0 0 17.8 17.8L20 15.6" />
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

const navItems = [
    { key: "my", label: "My files", icon: Icons.File },
    { key: "shared", label: "Shared", icon: Icons.Shared },
    { key: "recent", label: "Recent", icon: Icons.Clock },
    { key: "favorites", label: "Favorites", icon: Icons.Star },
    { key: "archived", label: "Archived", icon: Icons.Archive },
    { key: "trash", label: "Trash", icon: Icons.Trash },
];

function normalizePrefix(prefix) {
    if (!prefix) return "";
    return prefix.endsWith("/") ? prefix : `${prefix}/`;
}

function fileOriginalParentPrefix(file) {
    const key = file.originalObjectKey || "";
    const lastSlash = key.lastIndexOf("/");

    if (lastSlash === -1) {
        return "";
    }

    return key.slice(0, lastSlash + 1);
}

function folderParentPrefix(folder) {
    const prefix = normalizePrefix(folder.prefix);
    const withoutTrailingSlash = prefix.endsWith("/")
        ? prefix.slice(0, -1)
        : prefix;

    const lastSlash = withoutTrailingSlash.lastIndexOf("/");

    if (lastSlash === -1) {
        return "";
    }

    return withoutTrailingSlash.slice(0, lastSlash + 1);
}

function formatBytes(bytes) {
    if (!bytes && bytes !== 0) return "—";
    if (bytes === 0) return "0 B";

    const units = ["B", "KB", "MB", "GB"];
    let value = bytes;
    let index = 0;

    while (value >= 1024 && index < units.length - 1) {
        value /= 1024;
        index += 1;
    }

    return `${value.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}

function getTypeLabel(type) {
    if (type === "folder") return "Folder";
    if (type === "application/pdf") return "PDF document";
    if (type && type.startsWith("image/")) return "Image";
    if (type && type.startsWith("video/")) return "Video";
    if (type && type.startsWith("audio/")) return "Audio";
    if (type === "application/zip") return "Archive";

    return type || "File";
}

function getFileIcon(type) {
    if (type === "folder") return Icons.Folder;
    if (type && type.startsWith("image/")) return Icons.Image;
    return Icons.File;
}

function getFileTag(item) {
    if (item.type === "folder") return "Folder";
    if (item.type === "application/pdf") return "PDF";
    if (item.type && item.type.startsWith("image/")) return "Image";
    if (item.type && item.type.startsWith("video/")) return "Video";
    if (item.type && item.type.startsWith("audio/")) return "Audio";
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

function originalNameFromKey(key) {
    if (!key) return "Unknown file";

    const parts = key.split("/").filter(Boolean);
    return parts[parts.length - 1] || key;
}

function getRootPrefixFromTrash(trash) {
    const prefixes = [];

    for (const folder of trash.folders) {
        if (folder.prefix) {
            prefixes.push(normalizePrefix(folder.prefix));
        }
    }

    for (const file of trash.files) {
        const parent = fileOriginalParentPrefix(file);

        if (parent) {
            prefixes.push(parent);
        }
    }

    if (prefixes.length === 0) {
        return "";
    }

    const first = prefixes[0];
    const parts = first.split("/").filter(Boolean);

    if (parts.length >= 2 && parts[0] === "users") {
        return `users/${parts[1]}/`;
    }

    return "";
}

export default function Trashcan() {
    const navigate = useNavigate();

    const [sidebarOpen, setSidebarOpen] = useState(false);
    const [logoutConfirm, setLogoutConfirm] = useState(false);
    const [loggingOut, setLoggingOut] = useState(false);

    const [trash, setTrash] = useState({
        files: [],
        folders: [],
    });

    const [currentPrefix, setCurrentPrefix] = useState("");
    const [trashBackStack, setTrashBackStack] = useState([]);
    const [trashForwardStack, setTrashForwardStack] = useState([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [query, setQuery] = useState("");

    async function loadTrashcan() {
        try {
            setError("");
            setLoading(true);

            const data = await getTrashcan();

            setTrash({
                files: data.files || [],
                folders: data.folders || [],
            });
        } catch (err) {
            setError(err.message || "Failed to load trashcan");
        } finally {
            setLoading(false);
        }
    }

    useEffect(() => {
        loadTrashcan();
    }, []);

    const rootPrefix = useMemo(() => {
        return getRootPrefixFromTrash(trash);
    }, [trash]);

    useEffect(() => {
        if (!currentPrefix && rootPrefix) {
            setCurrentPrefix(rootPrefix);
        }
    }, [rootPrefix, currentPrefix]);

    const currentNormalizedPrefix = normalizePrefix(currentPrefix || rootPrefix);

    const canGoBack = trashBackStack.length > 0;
    const canGoForward = trashForwardStack.length > 0;

    const visibleFolders = useMemo(() => {
        const prefix = currentNormalizedPrefix;

        return trash.folders
            .filter((folder) => folderParentPrefix(folder) === prefix)
            .map((folder) => ({
                id: `folder-${folder.id}`,
                rawId: folder.id,
                name: folder.name,
                type: "folder",
                prefix: normalizePrefix(folder.prefix),
                size: null,
                owner: "You",
                originalPath: folder.prefix,
            }));
    }, [trash.folders, currentNormalizedPrefix]);

    const visibleFiles = useMemo(() => {
        const prefix = currentNormalizedPrefix;

        return trash.files
            .filter((file) => fileOriginalParentPrefix(file) === prefix)
            .map((file) => ({
                id: `file-${file.id}`,
                rawId: file.id,
                name: file.fileName || originalNameFromKey(file.originalObjectKey),
                type: file.objectType || "file",
                size: file.size,
                owner: "You",
                objectKey: file.objectKey,
                originalObjectKey: file.originalObjectKey,
                originalPath: file.originalObjectKey,
            }));
    }, [trash.files, currentNormalizedPrefix]);

    const visibleItems = useMemo(() => {
        const combined = [...visibleFolders, ...visibleFiles];

        if (!query.trim()) {
            return combined;
        }

        const q = query.trim().toLowerCase();

        return combined.filter((item) => {
            return (
                item.name?.toLowerCase().includes(q) ||
                item.originalPath?.toLowerCase().includes(q) ||
                getTypeLabel(item.type).toLowerCase().includes(q)
            );
        });
    }, [visibleFolders, visibleFiles, query]);

    const totalDeletedCount = trash.files.length + trash.folders.length;

    const breadcrumbs = useMemo(() => {
        const prefix = currentNormalizedPrefix;

        if (!prefix || !rootPrefix || !prefix.startsWith(rootPrefix)) {
            return [
                {
                    label: "Trash",
                    prefix: rootPrefix,
                    current: true,
                },
            ];
        }

        const relative = prefix.slice(rootPrefix.length);
        const parts = relative.split("/").filter(Boolean);

        const crumbs = [
            {
                label: "Trash",
                prefix: rootPrefix,
                current: parts.length === 0,
            },
        ];

        let runningPrefix = rootPrefix;

        for (let i = 0; i < parts.length; i += 1) {
            const part = parts[i];
            runningPrefix += `${part}/`;

            crumbs.push({
                label: part,
                prefix: runningPrefix,
                current: i === parts.length - 1,
            });
        }

        return crumbs;
    }, [currentNormalizedPrefix, rootPrefix]);

    function navigateTrashPrefix(nextPrefix) {
        const normalizedNext = normalizePrefix(nextPrefix);
        const normalizedCurrent = normalizePrefix(currentPrefix || rootPrefix);

        if (!normalizedNext || normalizedNext === normalizedCurrent) {
            return;
        }

        setTrashBackStack((stack) => [...stack, normalizedCurrent]);
        setTrashForwardStack([]);
        setCurrentPrefix(normalizedNext);
    }

    function openFolder(item) {
        if (item.type !== "folder") {
            return;
        }

        setQuery("");
        navigateTrashPrefix(item.prefix);
    }

    function goBack() {
        setTrashBackStack((backStack) => {
            if (backStack.length === 0) {
                return backStack;
            }

            const previousPrefix = backStack[backStack.length - 1];
            const nextBackStack = backStack.slice(0, -1);

            setTrashForwardStack((forwardStack) => [
                normalizePrefix(currentPrefix || rootPrefix),
                ...forwardStack,
            ]);

            setCurrentPrefix(previousPrefix);

            return nextBackStack;
        });
    }

    function goForward() {
        setTrashForwardStack((forwardStack) => {
            if (forwardStack.length === 0) {
                return forwardStack;
            }

            const nextPrefix = forwardStack[0];
            const nextForwardStack = forwardStack.slice(1);

            setTrashBackStack((backStack) => [
                ...backStack,
                normalizePrefix(currentPrefix || rootPrefix),
            ]);

            setCurrentPrefix(nextPrefix);

            return nextForwardStack;
        });
    }

    function handleNavClick(key) {
        if (key === "my") {
            navigate("/main");
            return;
        }

        if (key === "trash") {
            navigate("/trashcan");
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
            navigate("/login", { replace: true });
        } catch (err) {
            setError(err.message || "Failed to log out");
        } finally {
            setLoggingOut(false);
        }
    }

    return (
        <div className="drive-page">
            <aside className={`drive-sidebar ${sidebarOpen ? "open" : "closed"}`}>
                <div className="sidebar-top">
                    <button
                        type="button"
                        className="menu-button"
                        onClick={() => setSidebarOpen((value) => !value)}
                        aria-label="Toggle sidebar"
                    >
                        <Icons.Menu className="svg-icon" />
                    </button>

                    {sidebarOpen && (
                        <>
                            <div className="workspace-mark">FS</div>
                            <div className="workspace-text">
                                <strong>File-Drive-Spring</strong>
                                <span>Workspace</span>
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
                                    className={`nav-item ${item.key === "trash" ? "active" : ""}`}
                                    onClick={() => handleNavClick(item.key)}
                                    title={item.label}
                                >
                                    <span className="nav-icon">
                                        <NavIcon className="svg-icon" />
                                    </span>

                                    {sidebarOpen && <span>{item.label}</span>}
                                </button>
                            );
                        })}
                    </nav>

                    {sidebarOpen && (
                        <div className="label-section">
                            <p>TRASHCAN</p>
                            <button type="button" onClick={loadTrashcan}>
                                <span className="dot final-dot" />
                                <span>{totalDeletedCount} deleted items</span>
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

                        {sidebarOpen && (
                            <span>
                                {loggingOut
                                    ? "Logging out..."
                                    : logoutConfirm
                                        ? "Confirm logout"
                                        : "Log out"}
                            </span>
                        )}
                    </button>

                    {sidebarOpen && (
                        <div className="storage-box">
                            <div className="storage-bar">
                                <div />
                            </div>
                            <p>Trash uses your storage until emptied.</p>
                        </div>
                    )}
                </div>
            </aside>

            <main className="drive-main">
                <header className="drive-header">
                    <div className="header-location">
                        {(canGoBack || canGoForward) && (
                            <div className="history-controls">
                                {canGoBack && (
                                    <button
                                        type="button"
                                        className="history-button"
                                        onClick={goBack}
                                        aria-label="Go back"
                                    >
                                        <Icons.ArrowLeft className="svg-icon" />
                                    </button>
                                )}

                                {canGoForward && (
                                    <button
                                        type="button"
                                        className="history-button"
                                        onClick={goForward}
                                        aria-label="Go forward"
                                    >
                                        <Icons.ArrowRight className="svg-icon" />
                                    </button>
                                )}
                            </div>
                        )}

                        <div className="breadcrumbs">
                            {breadcrumbs.map((crumb, index) => (
                                <span
                                    className="breadcrumb-part"
                                    key={`${crumb.prefix}-${index}`}
                                >
                                    {index > 0 && (
                                        <span className="breadcrumb-slash">/</span>
                                    )}

                                    <button
                                        type="button"
                                        className={crumb.current ? "current" : ""}
                                        onClick={() => navigateTrashPrefix(crumb.prefix)}
                                    >
                                        {crumb.label}
                                    </button>
                                </span>
                            ))}
                        </div>
                    </div>

                    <button
                        type="button"
                        className="invite-button"
                        onClick={loadTrashcan}
                        disabled={loading}
                    >
                        Refresh
                    </button>
                </header>

                <section className="toolbar">
                    <div className="search-box">
                        <Icons.Search className="search-icon" />
                        <input
                            value={query}
                            onChange={(event) => setQuery(event.target.value)}
                            placeholder="Search trash"
                        />
                        <kbd>/</kbd>
                    </div>

                    <div className="toolbar-actions">
                        <button type="button" onClick={loadTrashcan} disabled={loading}>
                            <Icons.Refresh className="button-icon" />
                            Refresh
                        </button>

                        <button
                            type="button"
                            className="primary-action"
                            disabled
                            title="Restore will be added later"
                        >
                            Restore
                        </button>
                    </div>
                </section>

                {error && <div className="trash-error-banner">{error}</div>}

                <section className="content-layout">
                    <div className="file-table-wrap">
                        <div className="file-table">
                            <div className="file-row table-head">
                                <div>Name</div>
                                <div>Owner</div>
                                <div>Type</div>
                                <div>Original location</div>
                                <div>Size</div>
                                <div />
                            </div>

                            {loading ? (
                                <div className="empty-state">Loading trash...</div>
                            ) : visibleItems.length === 0 ? (
                                <div className="empty-state">
                                    {query.trim()
                                        ? "No trash items match your search."
                                        : "This trash folder is empty."}
                                </div>
                            ) : (
                                visibleItems.map((item) => {
                                    const ItemIcon = getFileIcon(item.type);
                                    const tag = getFileTag(item);

                                    return (
                                        <button
                                            key={item.id}
                                            type="button"
                                            className="file-row trash-file-row"
                                            onDoubleClick={() => openFolder(item)}
                                            onClick={() => {
                                                if (item.type === "folder") {
                                                    openFolder(item);
                                                }
                                            }}
                                        >
                                            <div className="name-cell">
                                                <span className="file-icon">
                                                    <ItemIcon className="svg-icon" />
                                                </span>

                                                <span>
                                                    <strong>{item.name}</strong>
                                                    <small>
                                                        {item.type === "folder"
                                                            ? "Open deleted folder"
                                                            : "Deleted file"}
                                                    </small>
                                                </span>
                                            </div>

                                            <div className="owner-cell">
                                                <span className="avatar">YOU</span>
                                                <span>{item.owner}</span>
                                            </div>

                                            <div className="tags-cell">
                                                <span className={tagClass(tag)}>
                                                    {tag}
                                                </span>
                                            </div>

                                            <div>
                                                <span className="trash-path">
                                                    {item.originalPath || "—"}
                                                </span>
                                                <small>Original path</small>
                                            </div>

                                            <div className="size-cell">
                                                {item.type === "folder"
                                                    ? "—"
                                                    : formatBytes(item.size)}
                                            </div>

                                            <div className="row-actions" />
                                        </button>
                                    );
                                })
                            )}
                        </div>

                        <p className="item-count">
                            {loading
                                ? "Loading..."
                                : `${visibleItems.length} shown · ${totalDeletedCount} total deleted`}
                        </p>
                    </div>
                </section>
            </main>
        </div>
    );
}