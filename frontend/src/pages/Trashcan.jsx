import { useEffect, useMemo, useState } from "react";
import { clearTrash, deletePermanently, getTrashcan } from "../api/drive";
import DriveSidebar from "../components/DriveSidebar";
import ConfirmPermanentDeleteModal from "../components/ConfirmPermanentDeleteModal";
import ConfirmClearTrashModal from "../components/ConfirmClearTrashModal";
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
    More: ({ className }) => (
        <svg
            className={className}
            viewBox="0 0 24 24"
            fill="currentColor"
            aria-hidden="true"
        >
            <circle cx="12" cy="5" r="1.8" />
            <circle cx="12" cy="12" r="1.8" />
            <circle cx="12" cy="19" r="1.8" />
        </svg>
    ),
    Info: ({ className }) => (
        <Icon className={className}>
            <circle cx="12" cy="12" r="9" />
            <path d="M12 10v6" />
            <path d="M12 7h.01" />
        </Icon>
    ),
    Restore: ({ className }) => (
        <svg
            className={className}
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.8"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
        >
            <path d="M7.5 9H3.5V5" />
            <path d="M3.8 9A8.2 8.2 0 1 1 6.2 17.2" />
            <path d="M12 7.5V12L15.5 14" />
        </svg>
    ),
    DeleteForever: ({ className }) => (
        <Icon className={className}>
            <path d="M3 6h18" />
            <path d="M8 6V4h8v2" />
            <path d="M6 6l1 15h10l1-15" />
            <path d="m10 11 4 4" />
            <path d="m14 11-4 4" />
        </Icon>
    ),
};

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

function stripUuidPrefix(name) {
    if (!name) return name;

    return name.replace(
        /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}-/,
        ""
    );
}

function cleanOriginalPath(path, itemName = "") {
    if (!path) return "—";

    let cleaned = path.replaceAll("\\", "/");

    cleaned = cleaned.replace(/^users\/[^/]+\//, "");

    cleaned = cleaned
        .split("/")
        .map((part) => stripUuidPrefix(part))
        .join("/");

    const cleanItemName = stripUuidPrefix(itemName);

    if (cleanItemName && cleaned.endsWith(`/${cleanItemName}`)) {
        cleaned = cleaned.slice(0, -cleanItemName.length);
    }

    if (!cleaned) {
        return "/";
    }

    return cleaned;
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
    if (type === "application/pdf") return "application/pdf";
    if (type && type.startsWith("image/")) return type;
    if (type && type.startsWith("video/")) return type;
    if (type && type.startsWith("audio/")) return type;
    if (type === "application/zip") return "application/zip";
    return type || "File";
}

function getTagLabel(type) {
    if (type === "folder") return "Folder";
    if (type === "application/pdf") return "PDF";
    if (type && type.startsWith("image/")) return "Image";
    if (type && type.startsWith("video/")) return "Video";
    if (type && type.startsWith("audio/")) return "Audio";
    return "File";
}

function getFileIcon(type) {
    if (type === "folder") return Icons.Folder;
    if (type && type.startsWith("image/")) return Icons.Image;
    return Icons.File;
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
    return stripUuidPrefix(parts[parts.length - 1] || key);
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

export default function Trashcan({ sidebarOpen, onToggleSidebar }) {
    const [trash, setTrash] = useState({
        files: [],
        folders: [],
    });

    const [currentPrefix, setCurrentPrefix] = useState("");
    const [trashBackStack, setTrashBackStack] = useState([]);
    const [trashForwardStack, setTrashForwardStack] = useState([]);

    const [selectedIds, setSelectedIds] = useState([]);
    const [openMenuId, setOpenMenuId] = useState(null);

    const [permanentDeleteItems, setPermanentDeleteItems] = useState([]);
    const [permanentDeleteLoading, setPermanentDeleteLoading] = useState(false);

    const [clearTrashOpen, setClearTrashOpen] = useState(false);
    const [clearTrashLoading, setClearTrashLoading] = useState(false);

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

            setSelectedIds([]);
            setOpenMenuId(null);
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
                displayType: "Folder",
                prefix: normalizePrefix(folder.prefix),
                size: null,
                owner: "You",
                originalPath: cleanOriginalPath(folder.prefix, folder.name),
            }));
    }, [trash.folders, currentNormalizedPrefix]);

    const visibleFiles = useMemo(() => {
        const prefix = currentNormalizedPrefix;

        return trash.files
            .filter((file) => fileOriginalParentPrefix(file) === prefix)
            .map((file) => {
                const displayName =
                    file.fileName || originalNameFromKey(file.originalObjectKey);

                return {
                    id: `file-${file.id}`,
                    rawId: file.id,
                    name: displayName,
                    type: file.objectType || "file",
                    displayType: getTypeLabel(file.objectType || "file"),
                    size: file.size,
                    owner: "You",
                    objectKey: file.objectKey,
                    originalObjectKey: file.originalObjectKey,
                    originalPath: cleanOriginalPath(
                        file.originalObjectKey,
                        displayName
                    ),
                };
            });
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
                item.displayType?.toLowerCase().includes(q)
            );
        });
    }, [visibleFolders, visibleFiles, query]);

    const selectedItems = useMemo(() => {
        return visibleItems.filter((item) => selectedIds.includes(item.id));
    }, [visibleItems, selectedIds]);

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
        setSelectedIds([]);
        setOpenMenuId(null);
        setCurrentPrefix(normalizedNext);
    }

    function openFolder(item) {
        if (item.type !== "folder") {
            return;
        }

        setQuery("");
        setSelectedIds([]);
        setOpenMenuId(null);
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

            setSelectedIds([]);
            setOpenMenuId(null);
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

            setSelectedIds([]);
            setOpenMenuId(null);
            setCurrentPrefix(nextPrefix);

            return nextForwardStack;
        });
    }

    function isSelected(item) {
        return selectedIds.includes(item.id);
    }

    function getActionItems(item) {
        const currentSelectedItems = visibleItems.filter((currentItem) =>
            selectedIds.includes(currentItem.id)
        );

        if (currentSelectedItems.length > 1 && selectedIds.includes(item.id)) {
            return currentSelectedItems;
        }

        return [item];
    }

    function toggleSelected(item) {
        setOpenMenuId(null);

        setSelectedIds((currentSelected) => {
            if (currentSelected.includes(item.id)) {
                return currentSelected.filter((id) => id !== item.id);
            }

            return [...currentSelected, item.id];
        });
    }

    function handleRowDoubleClick(item) {
        if (item.type === "folder") {
            setSelectedIds([]);
            setOpenMenuId(null);
            openFolder(item);
        }
    }

    function handleRestore(item) {
        const actionItems = getActionItems(item);

        setOpenMenuId(null);
        setError(
            actionItems.length === 1
                ? `Restore coming soon for ${actionItems[0].name}`
                : `Restore coming soon for ${actionItems.length} selected items`
        );
    }

    function handleDeletePermanently(item) {
        const actionItems = getActionItems(item);

        setOpenMenuId(null);
        setPermanentDeleteItems(actionItems);
    }

    async function confirmPermanentDelete() {
        if (permanentDeleteItems.length === 0) {
            return;
        }

        const fileIds = permanentDeleteItems
            .filter((item) => item.type !== "folder")
            .map((item) => item.rawId);

        const folderIds = permanentDeleteItems
            .filter((item) => item.type === "folder")
            .map((item) => item.rawId);

        try {
            setError("");
            setPermanentDeleteLoading(true);

            await deletePermanently(fileIds, folderIds);

            setPermanentDeleteItems([]);
            setSelectedIds([]);
            setOpenMenuId(null);

            await loadTrashcan();
        } catch (err) {
            setError(err.message || "Failed to permanently delete selected items");
        } finally {
            setPermanentDeleteLoading(false);
        }
    }

    function cancelPermanentDelete() {
        if (permanentDeleteLoading) {
            return;
        }

        setPermanentDeleteItems([]);
    }

    function openClearTrashModal() {
        setOpenMenuId(null);
        setError("");
        setClearTrashOpen(true);
    }

    function cancelClearTrash() {
        if (clearTrashLoading) {
            return;
        }

        setClearTrashOpen(false);
    }

    async function confirmClearTrash() {
        try {
            setError("");
            setClearTrashLoading(true);

            await clearTrash();

            setClearTrashOpen(false);
            setPermanentDeleteItems([]);
            setSelectedIds([]);
            setOpenMenuId(null);
            setCurrentPrefix("");
            setTrashBackStack([]);
            setTrashForwardStack([]);

            await loadTrashcan();
        } catch (err) {
            setError(err.message || "Failed to clear trash");
        } finally {
            setClearTrashLoading(false);
        }
    }

    function handleProperties(item) {
        const actionItems = getActionItems(item);

        setOpenMenuId(null);

        if (actionItems.length > 1) {
            return;
        }

        const target = actionItems[0];

        setError(
            `Properties: ${target.name} · ${target.displayType} · ${
                target.originalPath || "No original path"
            }`
        );
    }

    return (
        <div className="drive-page">
            <DriveSidebar
                active="trash"
                sidebarOpen={sidebarOpen}
                onToggleSidebar={onToggleSidebar}
                username="admin"
                deletedCount={totalDeletedCount}
            />

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
                                        onClick={() =>
                                            navigateTrashPrefix(crumb.prefix)
                                        }
                                    >
                                        {crumb.label}
                                    </button>
                                </span>
                            ))}
                        </div>
                    </div>
                </header>

                <section className="toolbar">
                    <div className="search-box">
                        <Icons.Search className="search-icon" />
                        <input
                            value={query}
                            onChange={(event) => {
                                setQuery(event.target.value);
                                setOpenMenuId(null);
                            }}
                            placeholder="Search trash"
                        />
                        <kbd>/</kbd>
                    </div>

                    <div className="toolbar-actions">
                        <button
                            type="button"
                            onClick={loadTrashcan}
                            disabled={loading}
                        >
                            <Icons.Refresh className="button-icon" />
                            Refresh
                        </button>

                        <button
                            type="button"
                            className="primary-action"
                            disabled={selectedItems.length === 0}
                            title="Restore will be added later"
                            onClick={() => {
                                if (selectedItems.length > 0) {
                                    handleRestore(selectedItems[0]);
                                }
                            }}
                        >
                            Restore
                        </button>

                        <button
                            type="button"
                            className="danger-action"
                            disabled={totalDeletedCount === 0 || loading}
                            onClick={openClearTrashModal}
                        >
                            <Icons.DeleteForever className="button-icon" />
                            Clear trash
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
                                    const tag = getTagLabel(item.type);
                                    const selected = isSelected(item);
                                    const actionItems = getActionItems(item);
                                    const showProperties = actionItems.length === 1;

                                    return (
                                        <div
                                            key={item.id}
                                            role="button"
                                            tabIndex={0}
                                            className={`file-row trash-file-row ${
                                                selected ? "selected" : ""
                                            }`}
                                            onClick={() => toggleSelected(item)}
                                            onDoubleClick={() =>
                                                handleRowDoubleClick(item)
                                            }
                                            onKeyDown={(event) => {
                                                if (
                                                    event.key === "Enter" ||
                                                    event.key === " "
                                                ) {
                                                    event.preventDefault();
                                                    toggleSelected(item);
                                                }
                                            }}
                                        >
                                            <div className="name-cell">
                                                <span className="file-icon">
                                                    <ItemIcon className="svg-icon" />
                                                </span>

                                                <span>
                                                    <strong>{item.name}</strong>
                                                    <small>{item.displayType}</small>
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

                                            <div className="row-actions">
                                                <button
                                                    type="button"
                                                    className="row-more-button"
                                                    onClick={(event) => {
                                                        event.stopPropagation();

                                                        if (!selected) {
                                                            setSelectedIds([item.id]);
                                                        }

                                                        setOpenMenuId((currentId) =>
                                                            currentId === item.id
                                                                ? null
                                                                : item.id
                                                        );
                                                    }}
                                                    aria-label={`Actions for ${item.name}`}
                                                >
                                                    <Icons.More className="row-more-icon" />
                                                </button>

                                                {openMenuId === item.id && (
                                                    <div
                                                        className="row-action-menu"
                                                        onClick={(event) =>
                                                            event.stopPropagation()
                                                        }
                                                    >
                                                        <button
                                                            type="button"
                                                            onClick={() =>
                                                                handleRestore(item)
                                                            }
                                                        >
                                                            <Icons.Restore className="menu-action-icon" />
                                                            Restore
                                                        </button>

                                                        <button
                                                            type="button"
                                                            className="danger-menu-action"
                                                            onClick={() =>
                                                                handleDeletePermanently(
                                                                    item
                                                                )
                                                            }
                                                        >
                                                            <Icons.DeleteForever className="menu-action-icon" />
                                                            Delete permanently
                                                        </button>

                                                        {showProperties && (
                                                            <button
                                                                type="button"
                                                                onClick={() =>
                                                                    handleProperties(item)
                                                                }
                                                            >
                                                                <Icons.Info className="menu-action-icon" />
                                                                Properties
                                                            </button>
                                                        )}
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    );
                                })
                            )}
                        </div>

                        <p className="item-count">
                            {loading
                                ? "Loading..."
                                : selectedItems.length > 0
                                    ? `${selectedItems.length} selected · ${visibleItems.length} shown · ${totalDeletedCount} total deleted`
                                    : `${visibleItems.length} shown · ${totalDeletedCount} total deleted`}
                        </p>
                    </div>
                </section>
            </main>

            <ConfirmPermanentDeleteModal
                open={permanentDeleteItems.length > 0}
                count={permanentDeleteItems.length}
                loading={permanentDeleteLoading}
                onConfirm={confirmPermanentDelete}
                onCancel={cancelPermanentDelete}
            />

            <ConfirmClearTrashModal
                open={clearTrashOpen}
                count={totalDeletedCount}
                loading={clearTrashLoading}
                onConfirm={confirmClearTrash}
                onCancel={cancelClearTrash}
            />
        </div>
    );
}