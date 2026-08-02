import { useEffect, useState } from "react";
import { getFolder, getRootFolder } from "../api/drive";
import "./DestinationModal.css";

function FolderIcon() {
    return (
        <svg
            className="destination-folder-icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
        >
            <path d="M3 7h6l2 2h10v8.5A2.5 2.5 0 0 1 18.5 20h-13A2.5 2.5 0 0 1 3 17.5z" />
        </svg>
    );
}

export default function DestinationModal({ open, operation, item, onConfirm, onClose }) {
    const [folder, setFolder] = useState(null);
    const [path, setPath] = useState([]);
    const [loading, setLoading] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        if (!open) return;
        let cancelled = false;

        async function loadRoot() {
            try {
                setLoading(true);
                setError("");
                const root = await getRootFolder();
                if (!cancelled) {
                    setFolder(root);
                    setPath([{ id: root.id, name: root.name || "My Drive" }]);
                }
            } catch (err) {
                if (!cancelled) setError(err.message || "Failed to load folders");
            } finally {
                if (!cancelled) setLoading(false);
            }
        }

        loadRoot();
        return () => { cancelled = true; };
    }, [open]);

    if (!open) return null;

    async function navigateTo(target, pathIndex = null) {
        if (!target?.id || loading) return;
        try {
            setLoading(true);
            setError("");
            const next = await getFolder(target.id);
            setFolder(next);
            setPath((current) => pathIndex == null
                ? [...current, { id: target.id, name: target.name || "Folder" }]
                : current.slice(0, pathIndex + 1));
        } catch (err) {
            setError(err.message || "Failed to open folder");
        } finally {
            setLoading(false);
        }
    }

    async function confirm() {
        if (!folder?.id || submitting) return;
        try {
            setSubmitting(true);
            setError("");
            await onConfirm(folder.id);
        } catch (err) {
            setError(err.message || `Failed to ${operation} item`);
        } finally {
            setSubmitting(false);
        }
    }

    const sourceFolderId = item?.type === "folder" ? item.rawId : null;
    const folders = folder?.folders || [];
    const verb = operation === "copy" ? "Copy" : "Move";

    return (
        <div className="modal-overlay" role="presentation" onMouseDown={(e) => e.target === e.currentTarget && !submitting && onClose()}>
            <section className="destination-modal" role="dialog" aria-modal="true" aria-labelledby="destination-title">
                <header>
                    <h2 id="destination-title">{verb} “{item?.name}”</h2>
                    <p>Choose a destination folder.</p>
                </header>

                <nav className="destination-breadcrumbs" aria-label="Destination path">
                    {path.map((part, index) => (
                        <button key={part.id} type="button" disabled={loading || index === path.length - 1} onClick={() => navigateTo(part, index)}>
                            {index > 0 && <span>/</span>} {part.name}
                        </button>
                    ))}
                </nav>

                <div className="destination-list" aria-busy={loading}>
                    {loading && <div className="destination-status">Loading folders…</div>}
                    {!loading && folders.map((child) => {
                        const unavailable = child.id === sourceFolderId;
                        return (
                            <button key={child.id} type="button" disabled={unavailable} onDoubleClick={() => !unavailable && navigateTo(child)} onClick={() => !unavailable && navigateTo(child)}>
                                <FolderIcon />
                                <span>{child.name || "Untitled folder"}</span>
                                <span className="destination-chevron">›</span>
                            </button>
                        );
                    })}
                    {!loading && folders.length === 0 && <div className="destination-status">This folder has no subfolders.</div>}
                </div>

                {error && <p className="destination-error" role="alert">{error}</p>}

                <footer>
                    <span>Destination: <strong>{folder?.name || "—"}</strong></span>
                    <div>
                        <button className="destination-cancel" type="button" disabled={submitting} onClick={onClose}>Cancel</button>
                        <button className="destination-confirm" type="button" disabled={!folder?.id || loading || submitting || folder.id === sourceFolderId} onClick={confirm}>
                            {submitting ? `${verb}ing…` : `${verb} here`}
                        </button>
                    </div>
                </footer>
            </section>
        </div>
    );
}
