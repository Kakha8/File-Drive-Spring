import { useEffect, useMemo, useRef, useState } from "react";
import { logout as apiLogout } from "../api/auth";
import {
    createFolder,
    getFileBlob,
    getFilesZipBlob,
    getFolder,
    getFolderZipBlob,
    getMixedZipBlob,
    getRootFolder,
    renameFile,
    renameFolder,
    uploadFile,
} from "../api/drive";

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
    Plus: ({ className }) => (
        <Icon className={className}>
            <path d="M12 5v14M5 12h14" />
        </Icon>
    ),
    Upload: ({ className }) => (
        <Icon className={className}>
            <path d="M12 16V4" />
            <path d="m7 9 5-5 5 5" />
            <path d="M4 20h16" />
        </Icon>
    ),
    Check: ({ className }) => (
        <Icon className={className}>
            <path d="m20 6-11 11-5-5" />
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
    Rename: ({ className }) => (
        <Icon className={className}>
            <path d="M4 20h16" />
            <path d="m14.5 4.5 5 5" />
            <path d="M13 6 5 14l-1 5 5-1 8-8" />
        </Icon>
    ),
    Copy: ({ className }) => (
        <Icon className={className}>
            <rect x="8" y="8" width="12" height="12" rx="2" />
            <path d="M4 16V6a2 2 0 0 1 2-2h10" />
        </Icon>
    ),
    Cut: ({ className }) => (
        <Icon className={className}>
            <circle cx="6" cy="6" r="2" />
            <circle cx="6" cy="18" r="2" />
            <path d="M20 4 8 16" />
            <path d="M8 8l12 12" />
        </Icon>
    ),
    Properties: ({ className }) => (
        <Icon className={className}>
            <circle cx="12" cy="12" r="9" />
            <path d="M12 10v6" />
            <path d="M12 7h.01" />
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

function getFileIcon(type) {
    if (type === "folder") return Icons.Folder;
    if (type && type.startsWith("image/")) return Icons.Image;
    return Icons.File;
}

function getTypeLabel(type) {
    if (type === "folder") return "Folder";
    if (type === "application/pdf") return "PDF document";
    if (type && type.startsWith("image/")) return "Image";
    if (type && type.startsWith("video/")) return "Video";
    if (type && type.startsWith("audio/")) return "Audio";
    if (type === "application/zip") return "Archive";
    if (type === "application/vnd.openxmlformats-officedocument.wordprocessingml.document") {
        return "Word document";
    }
    return type || "File";
}

function tagClass(tag) {
    if (tag === "Folder") return "tag shared";
    if (tag === "PDF") return "tag final";
    if (tag === "Image") return "tag design";
    if (tag === "Video") return "tag draft";
    if (tag === "Audio") return "tag research";
    return "tag private";
}

function getFileTag(item) {
    if (item.type === "folder") return "Folder";
    if (item.type === "application/pdf") return "PDF";
    if (item.type && item.type.startsWith("image/")) return "Image";
    if (item.type && item.type.startsWith("video/")) return "Video";
    if (item.type && item.type.startsWith("audio/")) return "Audio";
    return "File";
}

function normalizeFolderItems(folderData) {
    if (!folderData) return [];

    const folders = (folderData.folders || []).map((folder) => ({
        id: `folder-${folder.id}`,
        rawId: folder.id,
        name: folder.name || "Untitled folder",
        type: "folder",
        owner: folderData.name || "You",
        ownerInitials: "ME",
        tag: "Folder",
        lastEdited: "—",
        time: "",
        size: "—",
        folder,
    }));

    const files = (folderData.files || [])
        .filter((file) => !file.deleted)
        .map((file) => ({
            id: `file-${file.id}`,
            rawId: file.id,
            name: file.fileName || "Untitled file",
            type: file.objectType || "file",
            owner: folderData.name || "You",
            ownerInitials: "ME",
            tag: getFileTag({ type: file.objectType || "file" }),
            lastEdited: "—",
            time: "",
            size: formatBytes(file.size),
            file,
        }));

    return [...folders, ...files];
}

function Main({ onLogout }) {
    const [sidebarOpen, setSidebarOpen] = useState(false);
    const [nav, setNav] = useState("my");
    const [query, setQuery] = useState("");
    const [currentFolder, setCurrentFolder] = useState(null);
    const [path, setPath] = useState([]);
    const [forwardStack, setForwardStack] = useState([]);
    const [selectedIds, setSelectedIds] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [viewer, setViewer] = useState(null);
    const [confirmLogout, setConfirmLogout] = useState(false);
    const [isLoggingOut, setIsLoggingOut] = useState(false);

    const [newMenuOpen, setNewMenuOpen] = useState(false);
    const [newFolderDraft, setNewFolderDraft] = useState(null);
    const [creatingFolder, setCreatingFolder] = useState(false);
    const newFolderInputRef = useRef(null);
    const draftHandledRef = useRef(false);

    const fileInputRef = useRef(null);
    const [uploading, setUploading] = useState(false);
    const [uploadProgress, setUploadProgress] = useState(0);
    const [uploadName, setUploadName] = useState("");
    const [openMenuId, setOpenMenuId] = useState(null);

    const [renamingItem, setRenamingItem] = useState(null);
    const renameInputRef = useRef(null);

    useEffect(() => {
        let cancelled = false;

        async function loadRoot() {
            try {
                setLoading(true);
                setError("");

                if (cancelled) return;

                const root = await getRootFolder();

                if (cancelled) return;

                setCurrentFolder(root);
                setPath([{ id: root.id, name: root.name || "My Drive" }]);
                setForwardStack([]);
                setSelectedIds([]);
            } catch (err) {
                if (cancelled) return;

                setError(err.message || "Failed to load folder");

                if (onLogout) {
                    onLogout();
                }
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        }

        loadRoot();

        return () => {
            cancelled = true;
        };
    }, [onLogout]);

    useEffect(() => {
        return () => {
            if (viewer?.url) {
                URL.revokeObjectURL(viewer.url);
            }
        };
    }, [viewer]);

    useEffect(() => {
        if (newFolderDraft && newFolderInputRef.current) {
            newFolderInputRef.current.focus();
            newFolderInputRef.current.select();
        }
    }, [newFolderDraft]);

    useEffect(() => {
        if (renamingItem && renameInputRef.current) {
            renameInputRef.current.focus();
            renameInputRef.current.select();
        }
    }, [renamingItem]);

    useEffect(() => {
        function closeMenus() {
            setOpenMenuId(null);
        }

        window.addEventListener("click", closeMenus);

        return () => {
            window.removeEventListener("click", closeMenus);
        };
    }, []);

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

    async function reloadCurrentFolder() {
        if (!currentFolder?.id) return;

        const folder =
            path.length <= 1 ? await getRootFolder() : await getFolder(currentFolder.id);

        setCurrentFolder(folder);
        setSelectedIds([]);
    }

    function openUploadPicker() {
        if (!currentFolder?.id || uploading) return;
        fileInputRef.current?.click();
    }

    async function handleUploadSelected(event) {
        const file = event.target.files?.[0];

        if (!file || !currentFolder?.id) return;

        try {
            setUploading(true);
            setUploadProgress(0);
            setUploadName(file.name);
            setError("");

            await uploadFile(currentFolder.id, file, (percent) => {
                setUploadProgress(percent);
            });

            await reloadCurrentFolder();
        } catch (err) {
            setError(err.message || "Failed to upload file");
        } finally {
            setUploading(false);
            setUploadProgress(0);
            setUploadName("");
            event.target.value = "";
        }
    }

    function startCreateFolder() {
        if (!currentFolder?.id || creatingFolder) return;

        const draftId = `draft-folder-${Date.now()}`;
        draftHandledRef.current = false;

        setNewMenuOpen(false);
        setError("");
        setNewFolderDraft({
            id: draftId,
            name: "New folder",
        });
        setSelectedIds([draftId]);
    }

    function cancelCreateFolder() {
        if (draftHandledRef.current) return;

        draftHandledRef.current = true;
        setNewFolderDraft(null);
        setSelectedIds([]);
    }

    async function commitNewFolderName(name) {
        if (draftHandledRef.current) return;

        draftHandledRef.current = true;

        const cleanName = name.trim();

        if (!cleanName) {
            setNewFolderDraft(null);
            setSelectedIds([]);
            return;
        }

        try {
            setCreatingFolder(true);
            setLoading(true);
            setError("");

            await createFolder(currentFolder.id, cleanName);
            setNewFolderDraft(null);
            setSelectedIds([]);
            await reloadCurrentFolder();
        } catch (err) {
            setError(err.message || "Failed to create folder");
            setNewFolderDraft(null);
            setSelectedIds([]);
        } finally {
            setCreatingFolder(false);
            setLoading(false);
        }
    }

    async function openFolder(folder) {
        if (!folder?.id) return;

        try {
            setLoading(true);
            setError("");

            const nextFolder = await getFolder(folder.id);

            setCurrentFolder(nextFolder);
            setPath((current) => [
                ...current,
                { id: folder.id, name: folder.name || "Folder" },
            ]);
            setForwardStack([]);
            setSelectedIds([]);
            setNewFolderDraft(null);
        } catch (err) {
            setError(err.message || "Failed to open folder");
        } finally {
            setLoading(false);
        }
    }

    async function openFile(item) {
        if (!item?.file?.id) return;

        if (viewer?.url) {
            URL.revokeObjectURL(viewer.url);
        }

        try {
            setViewer({
                item,
                url: "",
                loading: true,
                error: "",
            });

            const blob = await getFileBlob(item.rawId);
            const url = URL.createObjectURL(blob);

            setViewer({
                item,
                url,
                loading: false,
                error: "",
            });
        } catch (err) {
            setViewer({
                item,
                url: "",
                loading: false,
                error: err.message || "Failed to open file",
            });
        }
    }

    function closeViewer() {
        if (viewer?.url) {
            URL.revokeObjectURL(viewer.url);
        }

        setViewer(null);
    }

    async function goToPathIndex(index) {
        const target = path[index];

        if (!target) return;

        try {
            setLoading(true);
            setError("");

            const folder =
                index === 0 ? await getRootFolder() : await getFolder(target.id);

            setCurrentFolder(folder);
            setPath((current) => current.slice(0, index + 1));
            setForwardStack([]);
            setSelectedIds([]);
            setNewFolderDraft(null);
        } catch (err) {
            setError(err.message || "Failed to open folder");
        } finally {
            setLoading(false);
        }
    }

    async function goBack() {
        if (path.length <= 1) return;

        const currentLocation = path[path.length - 1];
        const targetIndex = path.length - 2;
        const target = path[targetIndex];

        try {
            setLoading(true);
            setError("");

            const folder =
                targetIndex === 0 ? await getRootFolder() : await getFolder(target.id);

            setCurrentFolder(folder);
            setPath((current) => current.slice(0, targetIndex + 1));
            setForwardStack((current) => [currentLocation, ...current]);
            setSelectedIds([]);
            setNewFolderDraft(null);
        } catch (err) {
            setError(err.message || "Failed to go back");
        } finally {
            setLoading(false);
        }
    }

    async function goForward() {
        if (forwardStack.length === 0) return;

        const nextLocation = forwardStack[0];

        try {
            setLoading(true);
            setError("");

            const folder = await getFolder(nextLocation.id);

            setCurrentFolder(folder);
            setPath((current) => [...current, nextLocation]);
            setForwardStack((current) => current.slice(1));
            setSelectedIds([]);
            setNewFolderDraft(null);
        } catch (err) {
            setError(err.message || "Failed to go forward");
        } finally {
            setLoading(false);
        }
    }

    async function handleDownload(item) {
        try {
            setError("");

            const selectedItems = allItems.filter((currentItem) =>
                selectedIds.includes(currentItem.id)
            );

            const shouldDownloadSelection =
                selectedItems.length > 1 && selectedIds.includes(item.id);

            if (shouldDownloadSelection) {
                const fileIds = selectedItems
                    .filter((currentItem) => currentItem.type !== "folder")
                    .map((currentItem) => currentItem.rawId);

                const folderIds = selectedItems
                    .filter((currentItem) => currentItem.type === "folder")
                    .map((currentItem) => currentItem.rawId);

                const blob = await getMixedZipBlob(fileIds, folderIds);
                downloadBlob(blob, "download.zip");
                return;
            }

            if (item.type === "folder") {
                const blob = await getFolderZipBlob(item.rawId);
                downloadBlob(blob, `${item.name || "folder"}.zip`);
                return;
            }

            const blob = await getFileBlob(item.rawId);
            downloadBlob(blob, item.name || "download");
        } catch (err) {
            setError(err.message || "Failed to download item");
        }
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
    function handleRename(item) {
        setError("");
        setOpenMenuId(null);
        setSelectedIds([item.id]);
        setRenamingItem({
            id: item.id,
            rawId: item.rawId,
            type: item.type,
            name: item.name,
        });
    }

    function cancelRename() {
        setRenamingItem(null);
    }

    async function commitRename(item, value) {
        const cleanName = value.trim();

        if (!cleanName || cleanName === item.name) {
            setRenamingItem(null);
            return;
        }

        try {
            setError("");

            if (item.type === "folder") {
                await renameFolder(item.rawId, cleanName);
            } else {
                await renameFile(item.rawId, cleanName);
            }

            setRenamingItem(null);
            await reloadCurrentFolder();
        } catch (err) {
            setError(err.message || "Failed to rename item");
            setRenamingItem(null);
        }
    }

    function handleCopy(item) {
        setError(`Copy coming soon for ${item.name}`);
    }

    function handleCut(item) {
        setError(`Cut coming soon for ${item.name}`);
    }

    function handleDelete(item) {
        setError(`Delete coming soon for ${item.name}`);
    }

    function handleProperties(item) {
        setViewer({
            item,
            url: "",
            loading: false,
            error: "",
            propertiesOnly: true,
        });
    }

    function handleFileSelect(event, itemId) {
        const multiSelect = event.ctrlKey || event.metaKey;

        setSelectedIds((currentSelected) => {
            if (!multiSelect) {
                return [itemId];
            }

            if (currentSelected.includes(itemId)) {
                return currentSelected.filter((id) => id !== itemId);
            }

            return [...currentSelected, itemId];
        });
    }

    const allItems = useMemo(() => {
        const items = normalizeFolderItems(currentFolder);

        if (!newFolderDraft) {
            return items;
        }

        return [
            {
                id: newFolderDraft.id,
                rawId: null,
                name: newFolderDraft.name,
                type: "folder",
                owner: currentFolder?.name || "You",
                ownerInitials: "ME",
                tag: "Folder",
                lastEdited: "—",
                time: "",
                size: "—",
                folder: null,
                isDraft: true,
            },
            ...items,
        ];
    }, [currentFolder, newFolderDraft]);

    const visibleFiles = useMemo(() => {
        return allItems.filter((item) =>
            `${item.name} ${item.owner} ${item.type}`
                .toLowerCase()
                .includes(query.toLowerCase())
        );
    }, [allItems, query]);

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
                                <span>{path[0]?.name || "My Drive"}</span>
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
                                        setNav(item.key);
                                        setConfirmLogout(false);
                                    }}
                                    title={!sidebarOpen ? item.label : undefined}
                                    className={`nav-item ${nav === item.key ? "active" : ""}`}
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

                            {path.map((part, index) => (
                                <button
                                    key={`${part.id}-${index}`}
                                    type="button"
                                    onClick={() => goToPathIndex(index)}
                                >
                                    <span className="dot shared-dot" />
                                    {part.name}
                                </button>
                            ))}
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
                        {(path.length > 1 || forwardStack.length > 0) && (
                            <div className="history-controls">
                                {path.length > 1 && (
                                    <button
                                        className="history-button"
                                        type="button"
                                        onClick={goBack}
                                        disabled={loading}
                                        title="Go back"
                                    >
                                        <Icons.ArrowLeft className="history-icon" />
                                    </button>
                                )}

                                {forwardStack.length > 0 && (
                                    <button
                                        className="history-button"
                                        type="button"
                                        onClick={goForward}
                                        disabled={loading}
                                        title="Go forward"
                                    >
                                        <Icons.ArrowRight className="history-icon" />
                                    </button>
                                )}
                            </div>
                        )}

                        <div className="breadcrumbs">
                            {path.map((part, index) => (
                                <span key={`${part.id}-${index}`} className="breadcrumb-part">
                                    {index > 0 && <span className="breadcrumb-slash">/</span>}

                                    <button
                                        type="button"
                                        onClick={() => goToPathIndex(index)}
                                        className={index === path.length - 1 ? "current" : ""}
                                    >
                                        {part.name}
                                    </button>
                                </span>
                            ))}
                        </div>
                    </div>

                    <button className="invite-button" type="button">
                        Invite
                    </button>
                </header>

                <div className="toolbar">
                    <div className="search-box">
                        <Icons.Search className="search-icon" />

                        <input
                            value={query}
                            onChange={(event) => setQuery(event.target.value)}
                            placeholder="Search files"
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

                        <div className="new-menu-wrap">
                            <button
                                className="primary-action"
                                type="button"
                                onClick={() => setNewMenuOpen((open) => !open)}
                            >
                                <Icons.Plus className="button-icon" />
                                New
                            </button>

                            {newMenuOpen && (
                                <div className="new-menu">
                                    <button type="button" onClick={startCreateFolder}>
                                        <Icons.Folder className="button-icon" />
                                        Folder
                                    </button>

                                    <button type="button">
                                        <Icons.File className="button-icon" />
                                        Text
                                    </button>

                                    <button type="button">
                                        <Icons.File className="button-icon" />
                                        Docx
                                    </button>
                                </div>
                            )}
                        </div>

                        <button
                            className="primary-action"
                            type="button"
                            onClick={openUploadPicker}
                            disabled={uploading}
                        >
                            <Icons.Upload className="button-icon" />
                            {uploading ? `${uploadProgress}%` : "Upload"}
                        </button>

                        <input
                            ref={fileInputRef}
                            type="file"
                            className="hidden-file-input"
                            onChange={handleUploadSelected}
                        />
                    </div>
                </div>

                {uploading && (
                    <div className="upload-progress-wrap">
                        <div className="upload-progress-info">
                            <span>Uploading {uploadName}</span>
                            <span>{uploadProgress}%</span>
                        </div>

                        <div className="upload-progress-bar">
                            <div style={{ width: `${uploadProgress}%` }} />
                        </div>
                    </div>
                )}

                <div className="content-layout">
                    <section className="file-table-wrap">
                        <div className="file-table">
                            <div className="file-row table-head">
                                <div>Name</div>
                                <div>Owner</div>
                                <div>Tags</div>
                                <div>Last edited</div>
                                <div>Size</div>
                                <div />
                            </div>

                            {loading && !newFolderDraft && !uploading && (
                                <div className="empty-state">Loading files...</div>
                            )}

                            {!loading && error && (
                                <div className="empty-state">{error}</div>
                            )}

                            {!error &&
                                visibleFiles.map((item) => (
                                    <FileRow
                                        key={item.id}
                                        item={item}
                                        selected={selectedIds.includes(item.id)}
                                        isDraft={item.isDraft}
                                        draftInputRef={newFolderInputRef}
                                        creatingFolder={creatingFolder}
                                        openMenuId={openMenuId}
                                        setOpenMenuId={setOpenMenuId}
                                        renamingItem={renamingItem}
                                        renameInputRef={renameInputRef}
                                        onRenameCommit={commitRename}
                                        onRenameCancel={cancelRename}
                                        onDraftCommit={commitNewFolderName}
                                        onDraftCancel={cancelCreateFolder}
                                        onDownload={handleDownload}
                                        onRename={handleRename}
                                        onCopy={handleCopy}
                                        onCut={handleCut}
                                        onDelete={handleDelete}
                                        onProperties={handleProperties}
                                        onSelect={(event) =>
                                            handleFileSelect(event, item.id)
                                        }
                                        onOpen={() => {
                                            if (item.isDraft || renamingItem?.id === item.id) return;

                                            if (item.type === "folder") {
                                                openFolder(item.folder);
                                            } else {
                                                openFile(item);
                                            }
                                        }}
                                    />
                                ))}

                            {!loading && !error && visibleFiles.length === 0 && (
                                <div className="empty-state">This folder is empty.</div>
                            )}
                        </div>

                        <p className="item-count">
                            {visibleFiles.length} items
                            {selectedIds.length > 0 &&
                                ` · ${selectedIds.length} selected`}
                        </p>
                    </section>
                </div>
            </section>

            {viewer && <FileViewer viewer={viewer} onClose={closeViewer} />}
        </main>
    );
}

function FileRow({
                     item,
                     selected,
                     onSelect,
                     onOpen,
                     isDraft,
                     draftInputRef,
                     onDraftCommit,
                     onDraftCancel,
                     creatingFolder,
                     openMenuId,
                     setOpenMenuId,
                     renamingItem,
                     renameInputRef,
                     onRenameCommit,
                     onRenameCancel,
                     onDownload,
                     onRename,
                     onCopy,
                     onCut,
                     onDelete,
                     onProperties,
                 }) {
    const FileIcon = getFileIcon(item.type);
    const menuOpen = openMenuId === item.id;
    const isRenaming = renamingItem?.id === item.id;

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
            onClick={(event) => {
                if (isRenaming) return;
                onSelect(event);
            }}
            onDoubleClick={() => {
                if (isRenaming) return;
                onOpen();
            }}
            className={`file-row ${selected ? "selected" : ""}`}
            type="button"
        >
            <div className="name-cell">
                <span className="file-icon">
                    {selected ? (
                        <Icons.Check className="svg-icon" />
                    ) : (
                        <FileIcon className="svg-icon" />
                    )}
                </span>

                <span>
                    {isDraft ? (
                        <input
                            ref={draftInputRef}
                            className="new-folder-name-input"
                            defaultValue={item.name}
                            disabled={creatingFolder}
                            onClick={(event) => event.stopPropagation()}
                            onDoubleClick={(event) => event.stopPropagation()}
                            onBlur={(event) => onDraftCommit(event.target.value)}
                            onKeyDown={(event) => {
                                if (event.key === "Enter") {
                                    event.preventDefault();
                                    onDraftCommit(event.currentTarget.value);
                                }

                                if (event.key === "Escape") {
                                    event.preventDefault();
                                    onDraftCancel();
                                }
                            }}
                        />
                    ) : isRenaming ? (
                        <input
                            ref={renameInputRef}
                            className="new-folder-name-input"
                            defaultValue={item.name}
                            onClick={(event) => event.stopPropagation()}
                            onDoubleClick={(event) => event.stopPropagation()}
                            onBlur={(event) => onRenameCommit(item, event.target.value)}
                            onKeyDown={(event) => {
                                if (event.key === "Enter") {
                                    event.preventDefault();
                                    onRenameCommit(item, event.currentTarget.value);
                                }

                                if (event.key === "Escape") {
                                    event.preventDefault();
                                    onRenameCancel();
                                }
                            }}
                        />
                    ) : (
                        <strong>{item.name}</strong>
                    )}

                    <small>{isDraft ? "Folder" : getTypeLabel(item.type)}</small>
                </span>
            </div>

            <div className="owner-cell">
                <Avatar initials={item.ownerInitials} />
                <span>{item.owner}</span>
            </div>

            <div className="tags-cell">
                <TagPill tag={item.tag} />
            </div>

            <div>
                {item.lastEdited}
                {item.time && <small>{item.time}</small>}
            </div>

            <div className="size-cell">
                <span>{item.size}</span>
            </div>

            <div className="row-actions" onClick={(event) => event.stopPropagation()}>
                {!isDraft && (
                    <>
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

                                <button
                                    type="button"
                                    onClick={(event) => runAction(event, onRename)}
                                >
                                    <Icons.Rename className="menu-action-icon" />
                                    Rename
                                </button>

                                <button
                                    type="button"
                                    onClick={(event) => runAction(event, onCopy)}
                                >
                                    <Icons.Copy className="menu-action-icon" />
                                    Copy
                                </button>

                                <button
                                    type="button"
                                    onClick={(event) => runAction(event, onCut)}
                                >
                                    <Icons.Cut className="menu-action-icon" />
                                    Cut
                                </button>

                                <button
                                    type="button"
                                    onClick={(event) => runAction(event, onDelete)}
                                    className="danger-menu-action"
                                >
                                    <Icons.Trash className="menu-action-icon" />
                                    Delete
                                </button>

                                <button
                                    type="button"
                                    onClick={(event) => runAction(event, onProperties)}
                                >
                                    <Icons.Properties className="menu-action-icon" />
                                    Properties
                                </button>
                            </div>
                        )}
                    </>
                )}
            </div>
        </button>
    );
}

function FileViewer({ viewer, onClose }) {
    const { item, url, loading, error } = viewer;
    const type = item.type || "";
    const isPdf = type === "application/pdf";
    const isImage = type.startsWith("image/");
    const isVideo = type.startsWith("video/");
    const isAudio = type.startsWith("audio/");
    const canPreview = isPdf || isImage || isVideo || isAudio;

    if (viewer.propertiesOnly) {
        return (
            <div className="file-viewer-backdrop" onClick={onClose}>
                <section
                    className="file-viewer"
                    onClick={(event) => event.stopPropagation()}
                >
                    <header className="file-viewer-header">
                        <div>
                            <h2>{item.name}</h2>
                            <p>Properties</p>
                        </div>

                        <div className="file-viewer-actions">
                            <button type="button" onClick={onClose}>
                                ×
                            </button>
                        </div>
                    </header>

                    <div className="file-viewer-body">
                        <div className="viewer-message">
                            <p>
                                <strong>Name:</strong> {item.name}
                            </p>
                            <p>
                                <strong>Type:</strong> {getTypeLabel(item.type)}
                            </p>
                            <p>
                                <strong>Owner:</strong> {item.owner}
                            </p>
                            <p>
                                <strong>Size:</strong> {item.size}
                            </p>
                            <p>
                                <strong>Last edited:</strong> {item.lastEdited}
                            </p>
                        </div>
                    </div>
                </section>
            </div>
        );
    }

    return (
        <div className="file-viewer-backdrop" onClick={onClose}>
            <section
                className="file-viewer"
                onClick={(event) => event.stopPropagation()}
            >
                <header className="file-viewer-header">
                    <div>
                        <h2>{item.name}</h2>
                        <p>
                            {getTypeLabel(item.type)} · {item.size}
                        </p>
                    </div>

                    <div className="file-viewer-actions">
                        {url && (
                            <a
                                href={url}
                                download={item.name}
                                className="file-viewer-download"
                            >
                                Download
                            </a>
                        )}

                        <button type="button" onClick={onClose}>
                            ×
                        </button>
                    </div>
                </header>

                <div className="file-viewer-body">
                    {loading && <div className="viewer-message">Opening file...</div>}

                    {!loading && error && (
                        <div className="viewer-message viewer-error">{error}</div>
                    )}

                    {!loading && !error && url && isPdf && (
                        <iframe
                            title={item.name}
                            src={url}
                            className="viewer-frame"
                        />
                    )}

                    {!loading && !error && url && isImage && (
                        <img src={url} alt={item.name} className="viewer-image" />
                    )}

                    {!loading && !error && url && isVideo && (
                        <video src={url} controls className="viewer-video" />
                    )}

                    {!loading && !error && url && isAudio && (
                        <audio src={url} controls className="viewer-audio" />
                    )}

                    {!loading && !error && url && !canPreview && (
                        <div className="viewer-message">
                            <p>This file type cannot be previewed here.</p>
                            <a href={url} download={item.name}>
                                Download {item.name}
                            </a>
                        </div>
                    )}
                </div>
            </section>
        </div>
    );
}

function TagPill({ tag }) {
    return <span className={tagClass(tag)}>{tag}</span>;
}

function Avatar({ initials }) {
    return <span className="avatar">{initials}</span>;
}

export default Main;