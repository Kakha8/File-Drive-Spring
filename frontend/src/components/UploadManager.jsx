/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useRef, useState } from "react";
import { cancelUpload, uploadFile } from "../api/drive";
import { createClientId } from "../utils/clientId";

const UploadContext = createContext(null);
const NOTIFICATIONS_CHANGED_EVENT = "file-drive:notifications-changed";

function Icon({ children, className = "" }) {
    return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{children}</svg>;
}

function FileIcon({ className }) {
    return <Icon className={className}><path d="M6 3h8l4 4v14H6z" /><path d="M14 3v5h5" /></Icon>;
}

function CheckIcon({ className }) {
    return <Icon className={className}><path d="m5 12 4 4L19 6" /></Icon>;
}

function ChevronIcon({ className }) {
    return <Icon className={className}><path d="m7 10 5 5 5-5" /></Icon>;
}

export function UploadProvider({ children }) {
    const [uploads, setUploads] = useState([]);
    const [uploading, setUploading] = useState(false);
    const [minimized, setMinimized] = useState(false);
    const [closed, setClosed] = useState(false);
    const [cancelConfirm, setCancelConfirm] = useState(false);
    const uploadingRef = useRef(false);
    const cancelAllRef = useRef(false);
    const canceledIdsRef = useRef(new Set());
    const currentUploadRef = useRef(null);

    async function startUploads(files, parentId, onComplete) {
        if (!files.length || !parentId || uploadingRef.current) return;

        const items = files.map((file) => ({
            id: createClientId(),
            name: file.name,
            progress: 0,
            status: "waiting",
            error: "",
        }));

        setUploads(items);
        setUploading(true);
        uploadingRef.current = true;
        setClosed(false);
        setMinimized(false);
        setCancelConfirm(false);
        cancelAllRef.current = false;
        canceledIdsRef.current.clear();

        try {
            for (let index = 0; index < files.length; index += 1) {
                if (cancelAllRef.current) break;
                const file = files[index];
                const item = items[index];
                if (canceledIdsRef.current.has(item.id)) continue;

                setUploads((current) => current.map((entry) => entry.id === item.id ? { ...entry, status: "uploading", progress: 0, error: "" } : entry));
                const controller = new AbortController();
                currentUploadRef.current = { uploadId: item.id, controller };

                try {
                    await uploadFile(parentId, file, (progress) => {
                        if (cancelAllRef.current || canceledIdsRef.current.has(item.id)) return;
                        setUploads((current) => current.map((entry) => entry.id === item.id ? {
                            ...entry,
                            progress,
                            status: progress >= 100 ? "processing" : "uploading",
                        } : entry));
                    }, controller.signal, item.id);

                    if (!cancelAllRef.current && !canceledIdsRef.current.has(item.id) && !controller.signal.aborted) {
                        setUploads((current) => current.map((entry) => entry.id === item.id ? { ...entry, progress: 100, status: "done", error: "" } : entry));
                        window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED_EVENT));
                    }
                } catch (error) {
                    const canceled = cancelAllRef.current || canceledIdsRef.current.has(item.id) || controller.signal.aborted || error.code === "UPLOAD_CANCELED";
                    const malware = error.code === "MALWARE_DETECTED" || error.status === 422;
                    setUploads((current) => current.map((entry) => entry.id === item.id ? {
                        ...entry,
                        status: canceled ? "canceled" : "error",
                        error: canceled ? "Cancelled" : malware ? "Rejected: malware detected" : error.message || "Failed to upload this file",
                    } : entry));
                    if (malware && !canceled) window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED_EVENT));
                } finally {
                    if (currentUploadRef.current?.uploadId === item.id) currentUploadRef.current = null;
                }
            }

            if (!cancelAllRef.current) await onComplete?.();
        } finally {
            uploadingRef.current = false;
            setUploading(false);
        }
    }

    function closePanel() {
        const active = uploads.some((item) => ["waiting", "uploading", "processing"].includes(item.status));
        if (active) {
            setMinimized(false);
            setCancelConfirm(true);
        } else {
            setClosed(true);
        }
    }

    async function confirmCancel() {
        cancelAllRef.current = true;
        const activeIds = uploads.filter((item) => ["waiting", "uploading", "processing"].includes(item.status)).map((item) => item.id);
        activeIds.forEach((id) => canceledIdsRef.current.add(id));
        setUploads((current) => current.map((item) => activeIds.includes(item.id) ? { ...item, status: "canceled", error: "Cancelled" } : item));
        await Promise.allSettled(activeIds.map((id) => cancelUpload(id)));
        currentUploadRef.current?.controller.abort();
        currentUploadRef.current = null;
        uploadingRef.current = false;
        setUploading(false);
        setCancelConfirm(false);
        setClosed(true);
    }

    async function cancelSingle(uploadId) {
        canceledIdsRef.current.add(uploadId);
        setUploads((current) => current.map((item) => item.id === uploadId ? { ...item, status: "canceled", error: "Cancelled" } : item));
        await Promise.allSettled([cancelUpload(uploadId)]);
        if (currentUploadRef.current?.uploadId === uploadId) {
            currentUploadRef.current.controller.abort();
            currentUploadRef.current = null;
        }
    }

    const value = { uploading, startUploads };
    return <UploadContext.Provider value={value}>{children}<UploadPanel uploads={uploads} minimized={minimized} closed={closed} cancelConfirm={cancelConfirm} onToggleMinimized={() => setMinimized((value) => !value)} onClose={closePanel} onConfirmCancel={confirmCancel} onKeepUploading={() => setCancelConfirm(false)} onCancelUpload={cancelSingle} /></UploadContext.Provider>;
}

export function useUploads() {
    const context = useContext(UploadContext);
    if (!context) throw new Error("useUploads must be used inside UploadProvider");
    return context;
}

function UploadPanel({ uploads, minimized, closed, cancelConfirm, onToggleMinimized, onClose, onConfirmCancel, onKeepUploading, onCancelUpload }) {
    if (!uploads.length || closed) return null;
    const activeCount = uploads.filter((item) => ["waiting", "uploading", "processing"].includes(item.status)).length;
    const moving = uploads.filter((item) => item.status === "uploading" || item.status === "processing");
    const doneCount = uploads.filter((item) => item.status === "done").length;
    const errorCount = uploads.filter((item) => item.status === "error").length;
    const canceledCount = uploads.filter((item) => item.status === "canceled").length;
    const totalProgress = moving.length ? Math.round(moving.reduce((sum, item) => sum + (item.status === "processing" ? 100 : item.progress), 0) / moving.length) : 0;
    let title = "Upload complete";
    if (activeCount) title = `Uploading ${activeCount} ${activeCount === 1 ? "file" : "files"}`;
    else if (errorCount && doneCount) title = `${doneCount} uploaded, ${errorCount} failed`;
    else if (errorCount) title = "Upload failed";
    else if (canceledCount) title = `${canceledCount} ${canceledCount === 1 ? "upload" : "uploads"} cancelled`;

    return <div className={`upload-panel ${minimized ? "minimized" : ""}`}>
        <div className="upload-panel-header">
            <div className="upload-panel-title"><div className="upload-panel-title-row"><strong>{title}</strong>{minimized && activeCount > 0 && <span className="upload-panel-percent-wrap"><span className="upload-panel-circle-progress" style={{ "--progress": `${totalProgress * 3.6}deg` }} aria-hidden="true" /><span className="upload-panel-percent">{totalProgress}%</span></span>}</div><span>{doneCount}/{uploads.length}</span></div>
            <div className="upload-panel-actions"><button type="button" onClick={onToggleMinimized} title={minimized ? "Expand uploads" : "Minimize uploads"} aria-label={minimized ? "Expand uploads" : "Minimize uploads"}><ChevronIcon className={`upload-panel-chevron ${minimized ? "up" : ""}`} /></button><button type="button" onClick={onClose} title="Close upload panel" aria-label="Close upload panel">×</button></div>
        </div>
        {!minimized && cancelConfirm && <div className="upload-cancel-confirm"><strong>Cancel uploads?</strong><p>Uploads are still in progress. Cancel the remaining uploads?</p><div className="upload-cancel-actions"><button type="button" onClick={onKeepUploading}>Keep uploading</button><button type="button" className="danger" onClick={onConfirmCancel}>Cancel uploads</button></div></div>}
        {!minimized && !cancelConfirm && <div className="upload-panel-list">{uploads.map((upload) => <div key={upload.id} className="upload-panel-item"><div className="upload-file-icon">{upload.status === "done" ? <CheckIcon className="svg-icon" /> : <FileIcon className="svg-icon" />}</div><div className="upload-file-info"><div className="upload-file-line"><span title={upload.name}>{upload.name}</span><div className="upload-file-actions"><small>{upload.status === "waiting" && "Waiting"}{upload.status === "uploading" && `${upload.progress}%`}{upload.status === "processing" && "Scanning..."}{upload.status === "done" && "Done"}{upload.status === "error" && "Failed"}{upload.status === "canceled" && "Cancelled"}</small>{["waiting", "uploading", "processing"].includes(upload.status) && <button type="button" className="upload-item-cancel" onClick={() => onCancelUpload(upload.id)} title="Cancel this upload" aria-label={`Cancel upload ${upload.name}`}>×</button>}</div></div>{upload.status === "error" || upload.status === "canceled" ? <p className="upload-file-error">{upload.error}</p> : <div className="upload-file-progress"><div style={{ width: `${upload.progress}%` }} /></div>}</div></div>)}</div>}
    </div>;
}
