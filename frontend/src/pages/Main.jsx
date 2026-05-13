import { useMemo, useState } from "react";
import { logout as apiLogout } from "../api/auth";

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
    Figma: ({ className }) => (
        <Icon className={className}>
            <path d="M9 3h3v6H9a3 3 0 0 1 0-6Z" />
            <path d="M12 3h3a3 3 0 0 1 0 6h-3z" />
            <path d="M12 9h3a3 3 0 0 1 0 6h-3z" />
            <path d="M9 9h3v6H9a3 3 0 0 1 0-6Z" />
            <path d="M9 15h3v3a3 3 0 1 1-3-3Z" />
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
};

const navItems = [
    { key: "my", label: "My files", icon: Icons.File },
    { key: "shared", label: "Shared", icon: Icons.Shared },
    { key: "recent", label: "Recent", icon: Icons.Clock },
    { key: "favorites", label: "Favorites", icon: Icons.Star },
    { key: "archived", label: "Archived", icon: Icons.Archive },
    { key: "trash", label: "Trash", icon: Icons.Trash },
];

const files = [
    {
        id: "assets",
        name: "Assets",
        type: "folder",
        owner: "Olivia Rhye",
        ownerInitials: "OR",
        tags: ["Shared"],
        lastEdited: "May 16, 2024",
        time: "2:30 PM",
        size: "—",
        shared: true,
        favorite: true,
    },
    {
        id: "brand-refresh",
        name: "Brand Refresh",
        type: "folder",
        owner: "Liam Chen",
        ownerInitials: "LC",
        tags: ["Private"],
        lastEdited: "May 15, 2024",
        time: "10:21 AM",
        size: "—",
    },
    {
        id: "research",
        name: "Research",
        type: "folder",
        owner: "Olivia Rhye",
        ownerInitials: "OR",
        tags: ["Research", "Shared"],
        lastEdited: "May 10, 2024",
        time: "4:42 PM",
        size: "—",
        shared: true,
    },
    {
        id: "product-brief",
        name: "Product Brief.md",
        type: "markdown",
        owner: "Liam Chen",
        ownerInitials: "LC",
        tags: ["Draft", "Shared"],
        lastEdited: "May 16, 2024",
        time: "2:30 PM",
        size: "24 KB",
        shared: true,
        favorite: true,
    },
    {
        id: "design-system",
        name: "Design System.fig",
        type: "figma",
        owner: "Olivia Rhye",
        ownerInitials: "OR",
        tags: ["Design", "Shared"],
        lastEdited: "May 16, 2024",
        time: "12:45 PM",
        size: "8.4 MB",
        shared: true,
    },
    {
        id: "wireframe",
        name: "Homepage Wireframe.png",
        type: "image",
        owner: "Liam Chen",
        ownerInitials: "LC",
        tags: ["Design"],
        lastEdited: "May 15, 2024",
        time: "5:20 PM",
        size: "2.1 MB",
    },
    {
        id: "meeting-notes",
        name: "Meeting Notes.docx",
        type: "document",
        owner: "Olivia Rhye",
        ownerInitials: "OR",
        tags: ["Shared"],
        lastEdited: "May 14, 2024",
        time: "11:03 AM",
        size: "112 KB",
        shared: true,
    },
    {
        id: "marketing-plan",
        name: "Marketing Plan.pdf",
        type: "pdf",
        owner: "Olivia Rhye",
        ownerInitials: "OR",
        tags: ["Final", "Shared"],
        lastEdited: "May 12, 2024",
        time: "3:18 PM",
        size: "1.6 MB",
        shared: true,
    },
    {
        id: "competitive-analysis",
        name: "Competitive Analysis.xlsx",
        type: "sheet",
        owner: "Liam Chen",
        ownerInitials: "LC",
        tags: ["Private", "Research"],
        lastEdited: "May 11, 2024",
        time: "9:47 AM",
        size: "320 KB",
    },
    {
        id: "brand-guidelines",
        name: "Brand Guidelines.pdf",
        type: "pdf",
        owner: "Olivia Rhye",
        ownerInitials: "OR",
        tags: ["Final", "Shared"],
        lastEdited: "May 9, 2024",
        time: "4:28 PM",
        size: "4.7 MB",
        shared: true,
        favorite: true,
    },
    {
        id: "logo-variations",
        name: "Logo Variations.zip",
        type: "archive",
        owner: "Liam Chen",
        ownerInitials: "LC",
        tags: ["Shared"],
        lastEdited: "May 8, 2024",
        time: "1:15 PM",
        size: "12.3 MB",
        shared: true,
    },
];

function getFileIcon(type) {
    if (type === "folder") return Icons.Folder;
    if (type === "figma") return Icons.Figma;
    if (type === "image") return Icons.Image;
    return Icons.File;
}

function getTypeLabel(type) {
    if (type === "folder") return "Folder";
    if (type === "markdown") return "Markdown document";
    if (type === "figma") return "Figma design";
    if (type === "image") return "Image";
    if (type === "document") return "Word document";
    if (type === "pdf") return "PDF document";
    if (type === "sheet") return "Spreadsheet";
    if (type === "archive") return "Archive";
    return "File";
}

function tagClass(tag) {
    if (tag === "Shared") return "tag shared";
    if (tag === "Draft") return "tag draft";
    if (tag === "Design") return "tag design";
    if (tag === "Private") return "tag private";
    if (tag === "Final") return "tag final";
    return "tag research";
}

function filteredByNav(item, nav) {
    if (nav === "shared") return item.shared;
    if (nav === "favorites") return item.favorite;
    if (nav === "archived") return item.archived;
    if (nav === "trash") return false;
    return true;
}

function Main({ onLogout }) {
    const [sidebarOpen, setSidebarOpen] = useState(false);
    const [nav, setNav] = useState("my");
    const [query, setQuery] = useState("");
    const [selectedIds, setSelectedIds] = useState(["product-brief"]);
    const [confirmLogout, setConfirmLogout] = useState(false);
    const [isLoggingOut, setIsLoggingOut] = useState(false);

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

    function handleFileSelect(event, fileId) {
        const multiSelect = event.ctrlKey || event.metaKey;

        setSelectedIds((currentSelected) => {
            if (!multiSelect) {
                return [fileId];
            }

            if (currentSelected.includes(fileId)) {
                return currentSelected.filter((id) => id !== fileId);
            }

            return [...currentSelected, fileId];
        });
    }

    const visibleFiles = useMemo(() => {
        return files
            .filter((item) => filteredByNav(item, nav))
            .filter((item) =>
                `${item.name} ${item.owner} ${item.tags.join(" ")}`
                    .toLowerCase()
                    .includes(query.toLowerCase())
            )
            .sort((a, b) => {
                if (a.type === "folder" && b.type !== "folder") return -1;
                if (a.type !== "folder" && b.type === "folder") return 1;
                return 0;
            });
    }, [nav, query]);

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
                                <span>olivia@workspace.com</span>
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
                            <p>Labels</p>

                            {["Design", "Draft", "Shared", "Final"].map((label) => (
                                <button key={label} type="button">
                                    <span
                                        className={`dot ${
                                            label === "Design"
                                                ? "design-dot"
                                                : label === "Draft"
                                                    ? "draft-dot"
                                                    : label === "Final"
                                                        ? "final-dot"
                                                        : "shared-dot"
                                        }`}
                                    />
                                    {label}
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

                    {sidebarOpen && (
                        <div className="storage-box">
                            <div className="storage-bar">
                                <div />
                            </div>
                            <p>28.4 GB of 100 GB used</p>
                            <button type="button">Upgrade plan</button>
                        </div>
                    )}
                </div>
            </aside>

            <section className="drive-main">
                <header className="drive-header">
                    <div className="breadcrumbs">
                        <span>Workspace</span>
                        <span>/</span>
                        <span>Projects</span>
                        <span>/</span>
                        <strong>Brand Refresh</strong>
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

                        <button className="primary-action" type="button">
                            <Icons.Plus className="button-icon" />
                            New
                        </button>

                        <button className="primary-action" type="button">
                            <Icons.Upload className="button-icon" />
                            Upload
                        </button>
                    </div>
                </div>

                <div className="content-layout">
                    <section className="file-table-wrap">
                        <div className="file-table">
                            <div className="file-row table-head">
                                <div>Name</div>
                                <div>Owner</div>
                                <div>Tags</div>
                                <div>Last edited</div>
                                <div>Size</div>
                            </div>

                            {visibleFiles.map((item) => (
                                <FileRow
                                    key={item.id}
                                    item={item}
                                    selected={selectedIds.includes(item.id)}
                                    onSelect={(event) => handleFileSelect(event, item.id)}
                                />
                            ))}

                            {visibleFiles.length === 0 && (
                                <div className="empty-state">No files match this view.</div>
                            )}
                        </div>

                        <p className="item-count">
                            {visibleFiles.length} items
                            {selectedIds.length > 0 && ` · ${selectedIds.length} selected`}
                        </p>
                    </section>
                </div>
            </section>
        </main>
    );
}

function FileRow({ item, selected, onSelect }) {
    const FileIcon = getFileIcon(item.type);

    return (
        <button
            onClick={onSelect}
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
                    <strong>{item.name}</strong>
                    <small>{getTypeLabel(item.type)}</small>
                </span>
            </div>

            <div className="owner-cell">
                <Avatar initials={item.ownerInitials} />
                <span>{item.owner}</span>
            </div>

            <div className="tags-cell">
                {item.tags.slice(0, 2).map((tag) => (
                    <TagPill key={tag} tag={tag} />
                ))}
            </div>

            <div>
                {item.lastEdited}
                <small>{item.time}</small>
            </div>

            <div className="size-cell">
                <span>{item.size}</span>
                <Icons.More className="row-more-icon" />
            </div>
        </button>
    );
}

function TagPill({ tag }) {
    return <span className={tagClass(tag)}>{tag}</span>;
}

function Avatar({ initials }) {
    return <span className="avatar">{initials}</span>;
}

export default Main;