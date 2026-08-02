import { useEffect, useMemo, useState } from "react";
import DriveSidebar from "../components/DriveSidebar";
import NotificationMenu from "../components/NotificationMenu";
import UserMenu from "../components/UserMenu";
import { getFileBlob, getRecentFiles } from "../api/drive";

function Icon({ children, className = "" }) {
    return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{children}</svg>;
}

const FileIcon = ({ className }) => <Icon className={className}><path d="M6 3h8l4 4v14H6z"/><path d="M14 3v5h5"/></Icon>;
const SearchIcon = ({ className }) => <Icon className={className}><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></Icon>;

function formatBytes(bytes) {
    if (bytes == null) return "—";
    if (bytes === 0) return "0 B";
    const units = ["B", "KB", "MB", "GB", "TB"];
    let value = Number(bytes), index = 0;
    while (value >= 1024 && index < units.length - 1) { value /= 1024; index += 1; }
    return `${value.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}

function formatDate(value) {
    const date = value ? new Date(value) : null;
    if (!date || Number.isNaN(date.getTime())) return { date: "—", time: "" };
    return {
        date: new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }).format(date),
        time: new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(date),
    };
}

function fileType(type) {
    if (type === "application/pdf") return "PDF";
    if (type?.startsWith("image/")) return "Image";
    if (type?.startsWith("video/")) return "Video";
    if (type?.startsWith("audio/")) return "Audio";
    return "File";
}

export default function Recent({ sidebarOpen, onToggleSidebar, onLogout }) {
    const [files, setFiles] = useState([]);
    const [query, setQuery] = useState("");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => {
        let cancelled = false;
        getRecentFiles()
            .then((items) => { if (!cancelled) setFiles(Array.isArray(items) ? items : []); })
            .catch((err) => { if (!cancelled) setError(err.message || "Failed to load recent files"); })
            .finally(() => { if (!cancelled) setLoading(false); });
        return () => { cancelled = true; };
    }, []);

    const visibleFiles = useMemo(() => {
        const value = query.trim().toLowerCase();
        return value ? files.filter((file) => `${file.fileName} ${file.objectType}`.toLowerCase().includes(value)) : files;
    }, [files, query]);

    async function download(file) {
        try {
            const blob = await getFileBlob(file.id);
            const url = URL.createObjectURL(blob);
            const anchor = document.createElement("a");
            anchor.href = url;
            anchor.download = file.fileName;
            anchor.click();
            URL.revokeObjectURL(url);
        } catch (err) { setError(err.message || "Failed to download file"); }
    }

    return <div className="drive-page">
        <DriveSidebar active="recent" sidebarOpen={sidebarOpen} onToggleSidebar={onToggleSidebar} locationLabel="Recent" onLogoutComplete={onLogout}/>
        <main className="drive-main">
            <header className="drive-header">
                <div className="breadcrumbs"><strong>Recent</strong></div>
                <div className="drive-header-actions"><NotificationMenu onLogout={onLogout}/><UserMenu onLogout={onLogout}/></div>
            </header>
            <div className="toolbar">
                <div className="search-box"><SearchIcon className="search-icon"/><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search recent files"/><kbd>⌘ K</kbd></div>
            </div>
            <div className="content-layout">
                <section className="file-table-wrap">
                    <div className="file-table">
                        <div className="file-row table-head"><div>Name</div><div>Owner</div><div>Tags</div><div>Last edited</div><div>Size</div><div/></div>
                        {loading && <div className="empty-state">Loading...</div>}
                        {!loading && error && <div className="empty-state">{error}</div>}
                        {!loading && !error && visibleFiles.map((file) => {
                            const edited = formatDate(file.lastModifiedDate || file.creationDate);
                            const tag = fileType(file.objectType);
                            return <div className="file-row" key={file.id} onDoubleClick={() => download(file)} title="Double-click to download">
                                <div className="name-cell"><span className="file-icon"><FileIcon className="svg-icon"/></span><span><strong>{file.fileName}</strong><small>{file.objectType || "File"}</small></span></div>
                                <div className="owner-cell"><span className="avatar">ME</span><span>{file.ownerUsername || "You"}</span></div>
                                <div className="tags-cell"><span className="tag private">{tag}</span></div>
                                <div>{edited.date}{edited.time && <small>{edited.time}</small>}</div>
                                <div className="size-cell">{formatBytes(file.size)}</div><div/>
                            </div>;
                        })}
                        {!loading && !error && visibleFiles.length === 0 && <div className="empty-state">{query ? "No recent files match your search." : "No recently uploaded or modified files yet."}</div>}
                    </div>
                    {!loading && !error && <div className="item-count">{visibleFiles.length} {visibleFiles.length === 1 ? "file" : "files"}</div>}
                </section>
            </div>
        </main>
    </div>;
}
