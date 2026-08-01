import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { logout as apiLogout } from "../api/auth";
import { getFileBlob, getFolderZipBlob } from "../api/drive";
import { getSharedWithMe } from "../api/sharing";
import TextEditorModal from "../components/TextEditorModal";
import NotificationMenu from "../components/NotificationMenu";
import UserMenu from "../components/UserMenu";

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
    Filter: ({ className }) => (
        <Icon className={className}>
            <path d="M4 5h16l-6 7v5l-4 2v-7z" />
        </Icon>
    ),
    Sort: ({ className }) => (
        <Icon className={className}>
            <path d="M8 4v16" />
            <path d="m5 7 3-3 3 3" />
            <path d="M16 20V4" />
            <path d="m13 17 3 3 3-3" />
        </Icon>
    ),
    More: ({ className }) => (
        <Icon className={className}>
            <circle cx="12" cy="5" r="1" />
            <circle cx="12" cy="12" r="1" />
            <circle cx="12" cy="19" r="1" />
        </Icon>
    ),
    Download: ({ className }) => (
        <Icon className={className}>
            <path d="M12 3v12" />
            <path d="m7 10 5 5 5-5" />
            <path d="M5 21h14" />
        </Icon>
    ),
    Edit: ({ className }) => (
        <Icon className={className}>
            <path d="M12 20h9" />
            <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L8 18l-4 1 1-4Z" />
            <path d="m15 5 3 3" />
        </Icon>
    ),
};


const EDITABLE_EXTENSIONS = new Set([
    "txt",
    "md",
    "csv",
    "json",
    "xml",
    "yaml",
    "yml",
    "properties",
    "ini",
    "log",
    "html",
    "css",
    "js",
    "ts",
    "java",
    "py",
    "sql",
]);

function getFileExtension(fileName) {
    if (!fileName) return "";

    const lastDot = fileName.lastIndexOf(".");

    if (lastDot < 0 || lastDot === fileName.length - 1) {
        return "";
    }

    return fileName.substring(lastDot + 1).toLowerCase();
}

function canEditSharedFile(item) {
    return (
        item?.resourceType === "FILE" &&
        item?.role === "EDITOR" &&
        EDITABLE_EXTENSIONS.has(getFileExtension(item?.name))
    );
}

const navItems = [
    { key: "my", label: "My files", icon: Icons.File },
    { key: "shared", label: "Shared", icon: Icons.Shared },
    { key: "recent", label: "Recent", icon: Icons.Clock },
    { key: "favorites", label: "Favorites", icon: Icons.Star },
    { key: "archived", label: "Archived", icon: Icons.Archive },
    { key: "trash", label: "Trash", icon: Icons.Trash },
];

function getSharedType(item) {
    if (item.resourceType === "FOLDER") return "folder";

    const name = String(item.name || "").toLowerCase();

    if (name.endsWith(".pdf")) return "application/pdf";
    if (
        name.endsWith(".jpg") ||
        name.endsWith(".jpeg") ||
        name.endsWith(".png") ||
        name.endsWith(".webp") ||
        name.endsWith(".gif")
    ) {
        return "image/*";
    }

    if (name.endsWith(".zip")) return "application/zip";

    return "file";
}

function getFileIcon(type) {
    if (type === "folder") return Icons.Folder;
    if (type && type.startsWith("image/")) return Icons.Image;
    return Icons.File;
}


function formatLastEdited(value) {
    if (!value) {
        return { lastEdited: "—", time: "" };
    }

    const editedAt = new Date(value);

    if (Number.isNaN(editedAt.getTime())) {
        return { lastEdited: "—", time: "" };
    }

    const now = new Date();
    const todayKey = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
    const editedKey = Date.UTC(
        editedAt.getFullYear(),
        editedAt.getMonth(),
        editedAt.getDate()
    );
    const daysAgo = Math.round((todayKey - editedKey) / 86_400_000);

    let lastEdited;

    if (daysAgo === 0) {
        lastEdited = "Today";
    } else if (daysAgo === 1) {
        lastEdited = "Yesterday";
    } else {
        lastEdited = new Intl.DateTimeFormat(undefined, {
            month: "short",
            day: "numeric",
            ...(editedAt.getFullYear() === now.getFullYear()
                ? {}
                : { year: "numeric" }),
        }).format(editedAt);
    }

    const time = new Intl.DateTimeFormat(undefined, {
        hour: "numeric",
        minute: "2-digit",
    }).format(editedAt);

    return { lastEdited, time };
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

function getTypeLabel(item) {
    if (item.resourceType === "FOLDER") return "Folder";
    if (item.type === "application/pdf") return "PDF document";
    if (item.type && item.type.startsWith("image/")) return "Image";
    if (item.type === "application/zip") return "Archive";
    return "File";
}

function Avatar({ initials }) {
    return <span className="avatar">{initials}</span>;
}

function TagPill({ tag }) {
    const cleanTag = tag || "Shared";
    const className = cleanTag === "EDITOR" ? "tag shared" : "tag private";

    return <span className={className}>{cleanTag}</span>;
}

function normalizeSharedItems(items) {
    return (items || []).map((item) => {
        const type = getSharedType(item);
        const owner = item.ownerUsername || "Unknown";
        const edited = formatLastEdited(item.lastModifiedDate);

        return {
            id: `shared-${item.shareId}`,
            shareId: item.shareId,
            rawId: item.resourceId,
            name: item.name || "Untitled",
            resourceType: item.resourceType,
            type,
            owner,
            ownerInitials: owner.slice(0, 2).toUpperCase(),
            role: item.role || "VIEWER",
            lastModifiedDate: item.lastModifiedDate || null,
            lastEdited: edited.lastEdited,
            time: edited.time,
            size: formatBytes(item.size),
        };
    });
}

function downloadBlob(blob, fileName) {
    const url = URL.createObjectURL(blob);

    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();

    URL.revokeObjectURL(url);
}

export default function SharedWithMe({ onLogout }) {
    const navigate = useNavigate();

    const [sidebarOpen, setSidebarOpen] = useState(false);
    const [query, setQuery] = useState("");
    const [items, setItems] = useState([]);
    const [selectedIds, setSelectedIds] = useState([]);
    const [openMenuId, setOpenMenuId] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [editorTarget, setEditorTarget] = useState(null);
    const [confirmLogout, setConfirmLogout] = useState(false);
    const [isLoggingOut, setIsLoggingOut] = useState(false);

    useEffect(() => {
        let cancelled = false;

        async function loadShared() {
            try {
                setLoading(true);
                setError("");

                const shared = await getSharedWithMe();

                if (cancelled) return;

                setItems(normalizeSharedItems(shared));
                setSelectedIds([]);
            } catch (err) {
                if (cancelled) return;
                setError(err.message || "Failed to load shared items");
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        }

        loadShared();

        return () => {
            cancelled = true;
        };
    }, []);

    useEffect(() => {
        function closeMenus() {
            setOpenMenuId(null);
        }

        window.addEventListener("click", closeMenus);

        return () => {
            window.removeEventListener("click", closeMenus);
        };
    }, []);

    const visibleItems = useMemo(() => {
        const cleanQuery = query.trim().toLowerCase();

        if (!cleanQuery) return items;

        return items.filter((item) =>
            `${item.name} ${item.owner} ${item.role} ${item.resourceType}`
                .toLowerCase()
                .includes(cleanQuery)
        );
    }, [items, query]);

    async function handleLogout() {
        if (!confirmLogout) {
            setConfirmLogout(true);
            return;
        }

        try {
            setIsLoggingOut(true);
            await apiLogout();

            if (onLogout) {
                onLogout();
            }
        } finally {
            setIsLoggingOut(false);
        }
    }

    function handleSelect(event, itemId) {
        const multiSelect = event.ctrlKey || event.metaKey;

        setSelectedIds((current) => {
            if (!multiSelect) return [itemId];

            if (current.includes(itemId)) {
                return current.filter((id) => id !== itemId);
            }

            return [...current, itemId];
        });
    }

    async function handleDownload(item) {
        try {
            setError("");
            setOpenMenuId(null);

            if (item.resourceType === "FOLDER") {
                const blob = await getFolderZipBlob(item.rawId);
                downloadBlob(blob, `${item.name || "folder"}.zip`);
                return;
            }

            const blob = await getFileBlob(item.rawId);
            downloadBlob(blob, item.name || "download");
        } catch (err) {
            setError(err.message || "Failed to download shared item");
        }
    }

    function handleOpen(item) {
        handleDownload(item);
    }


    function handleEdit(item) {
        setOpenMenuId(null);

        if (!canEditSharedFile(item)) {
            setError("You do not have permission to edit this shared file.");
            return;
        }

        setError("");
        setSelectedIds([item.id]);
        setEditorTarget(item);
    }

    function handleEditorSaved(result) {
        const lastModifiedDate =
            result?.lastModifiedDate || new Date().toISOString();
        const edited = formatLastEdited(lastModifiedDate);

        setItems((currentItems) =>
            currentItems.map((currentItem) =>
                currentItem.rawId === result?.fileId
                    ? {
                        ...currentItem,
                        type: result.contentType || currentItem.type,
                        lastModifiedDate,
                        lastEdited: edited.lastEdited,
                        time: edited.time,
                        size: formatBytes(result?.size),
                    }
                    : currentItem
            )
        );
    }

    return (
        <main className="drive-page">
            <aside className={`drive-sidebar ${sidebarOpen ? "open" : "closed"}`}>
                <div className="sidebar-top">
                    <button
                        className="menu-button"
                        onClick={() => setSidebarOpen((open) => !open)}
                        aria-label={sidebarOpen ? "Collapse sidebar" : "Expand sidebar"}
                        title={sidebarOpen ? "Collapse sidebar" : "Expand sidebar"}
                        type="button"
                    >
                        <Icons.Menu className="svg-icon" />
                    </button>

                    {sidebarOpen && (
                        <>
                            <div className="workspace-mark">W</div>
                            <div className="workspace-text">
                                <strong>Workspace</strong>
                                <span>Shared with me</span>
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
                                    onClick={() => {
                                        setConfirmLogout(false);

                                        if (item.key === "my") {
                                            navigate("/");
                                            return;
                                        }

                                        if (item.key === "shared") {
                                            navigate("/shared");
                                            return;
                                        }

                                        if (item.key === "trash") {
                                            navigate("/trashcan");
                                            return;
                                        }
                                    }}
                                    title={!sidebarOpen ? item.label : undefined}
                                    className={`nav-item ${
                                        item.key === "shared" ? "active" : ""
                                    }`}
                                    type="button"
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
                            <p>Location</p>

                            <button type="button" onClick={() => navigate("/shared")}>
                                <span className="dot shared-dot" />
                                Shared with me
                            </button>
                        </div>
                    )}
                </div>

                <div className="sidebar-footer">
                    <button
                        className={`logout-action ${confirmLogout ? "confirming" : ""}`}
                        onClick={handleLogout}
                        title={confirmLogout ? "Confirm log out" : "Log out"}
                        disabled={isLoggingOut}
                        type="button"
                    >
                        <Icons.Logout className="svg-icon" />

                        {sidebarOpen && (
                            <span>
                                {isLoggingOut
                                    ? "Logging out..."
                                    : confirmLogout
                                        ? "Confirm log out"
                                        : "Log out"}
                            </span>
                        )}
                    </button>

                    {confirmLogout && sidebarOpen && (
                        <button
                            onClick={handleLogout}
                            disabled={isLoggingOut}
                            className="logout-confirm"
                            type="button"
                        >
                            {isLoggingOut ? "Logging out..." : "Yes, log me out"}
                        </button>
                    )}
                </div>
            </aside>

            <section className="drive-main">
                <header className="drive-header">
                    <div className="header-location">
                        <div className="breadcrumbs">
                            <span className="breadcrumb-part">
                                <button type="button" className="current">
                                    Shared with me
                                </button>
                            </span>
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
                            placeholder="Search shared files"
                        />

                        <kbd>⌘ K</kbd>
                    </div>

                    <div className="toolbar-actions">
                        <button type="button">
                            <Icons.Filter className="button-icon" />
                            Filter
                        </button>

                        <button type="button">
                            <Icons.Sort className="button-icon" />
                            Sort
                        </button>
                    </div>
                </div>

                <div className="content-layout">
                    <section className="file-table-wrap">
                        <div className="file-table">
                            <div className="file-row table-head">
                                <div>Name</div>
                                <div>Owner</div>
                                <div>Permission</div>
                                <div>Last edited</div>
                                <div>Size</div>
                                <div />
                            </div>

                            {loading && (
                                <div className="empty-state">
                                    Loading shared items...
                                </div>
                            )}

                            {!loading && error && (
                                <div className="empty-state">{error}</div>
                            )}

                            {!loading &&
                                !error &&
                                visibleItems.map((item) => (
                                    <SharedFileRow
                                        key={item.id}
                                        item={item}
                                        selected={selectedIds.includes(item.id)}
                                        openMenuId={openMenuId}
                                        setOpenMenuId={setOpenMenuId}
                                        onSelect={(event) =>
                                            handleSelect(event, item.id)
                                        }
                                        onOpen={() => handleOpen(item)}
                                        onDownload={handleDownload}
                                        onEdit={handleEdit}
                                    />
                                ))}

                            {!loading && !error && visibleItems.length === 0 && (
                                <div className="empty-state">
                                    No files or folders have been shared with you.
                                </div>
                            )}
                        </div>

                        <p className="item-count">
                            {visibleItems.length} items
                            {selectedIds.length > 0 &&
                                ` · ${selectedIds.length} selected`}
                        </p>
                    </section>
                </div>
            </section>

            {editorTarget && (
                <TextEditorModal
                    item={editorTarget}
                    onClose={() => setEditorTarget(null)}
                    onSaved={handleEditorSaved}
                />
            )}
        </main>
    );
}

function SharedFileRow({
                           item,
                           selected,
                           onSelect,
                           onOpen,
                           onDownload,
                           onEdit,
                           openMenuId,
                           setOpenMenuId,
                       }) {
    const FileIcon = getFileIcon(item.type);
    const menuOpen = openMenuId === item.id;

    function handleMenuButtonClick(event) {
        event.stopPropagation();
        setOpenMenuId(menuOpen ? null : item.id);
    }

    function runAction(event, action) {
        event.stopPropagation();
        setOpenMenuId(null);
        action?.(item);
    }

    return (
        <button
            onClick={onSelect}
            onDoubleClick={onOpen}
            className={`file-row ${selected ? "selected" : ""}`}
            type="button"
        >
            <div className="name-cell">
                <span className="file-icon">
                    <FileIcon className="svg-icon" />
                </span>

                <span>
                    <span className="item-title-line">
                        <strong>{item.name}</strong>

                        <span
                            className="shared-indicator"
                            title="Shared with me"
                            aria-label="Shared with me"
                        >
                            <Icons.Shared className="shared-indicator-icon" />
                        </span>
                    </span>

                    <small>{getTypeLabel(item)}</small>
                </span>
            </div>

            <div className="owner-cell">
                <Avatar initials={item.ownerInitials} />
                <span>{item.owner}</span>
            </div>

            <div className="tags-cell">
                <TagPill tag={item.role} />
            </div>

            <div title={item.lastModifiedDate || undefined}>
                {item.lastEdited}
                {item.time && <small>{item.time}</small>}
            </div>

            <div className="size-cell">
                <span>{item.size}</span>
            </div>

            <div className="row-actions" onClick={(event) => event.stopPropagation()}>
                <button
                    className="row-more-button"
                    type="button"
                    onClick={handleMenuButtonClick}
                    aria-label={`Open actions for ${item.name}`}
                >
                    <Icons.More className="row-more-icon" />
                </button>

                {menuOpen && (
                    <div className="row-action-menu">
                        <button
                            type="button"
                            onClick={(event) => runAction(event, onDownload)}
                        >
                            <Icons.Download className="menu-action-icon" />
                            Download
                        </button>


                        {canEditSharedFile(item) && (
                            <button
                                type="button"
                                onClick={(event) => runAction(event, onEdit)}
                            >
                                <Icons.Edit className="menu-action-icon" />
                                Edit
                            </button>
                        )}
                    </div>
                )}
            </div>
        </button>
    );
}