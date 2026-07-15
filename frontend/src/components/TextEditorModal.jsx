import { useEffect, useMemo, useRef, useState } from "react";
import { apiFetch } from "../api/http";
import "./TextEditorModal.css";

async function createApiError(response, fallbackMessage) {
    const responseText = await response.text();
    let payload = null;

    try {
        payload = responseText ? JSON.parse(responseText) : null;
    } catch {
        payload = null;
    }

    const error = new Error(
        payload?.message || responseText || fallbackMessage
    );

    error.status = response.status;
    error.code = payload?.error;

    return error;
}

async function getEditableFileContent(fileId) {
    const response = await apiFetch(`/api/files/${fileId}/content`);

    if (!response.ok) {
        throw await createApiError(response, "Failed to load file content");
    }

    return response.json();
}

async function updateEditableFileContent(fileId, content, expectedChecksum) {
    const body = { content };

    if (expectedChecksum) {
        body.expectedChecksum = expectedChecksum;
    }

    const response = await apiFetch(`/api/files/${fileId}/content`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
    });

    if (!response.ok) {
        throw await createApiError(response, "Failed to save file content");
    }

    return response.json();
}

function TextEditorModal({ item, onClose, onSaved }) {
    const [content, setContent] = useState("");
    const [originalContent, setOriginalContent] = useState("");
    const [checksum, setChecksum] = useState("");
    const [contentType, setContentType] = useState(item.type || "text/plain");
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");
    const [errorStatus, setErrorStatus] = useState(null);
    const textareaRef = useRef(null);

    const dirty = !loading && content !== originalContent;
    const byteCount = useMemo(
        () => new TextEncoder().encode(content).length,
        [content]
    );
    const lineCount = useMemo(
        () => (content.length === 0 ? 1 : content.split(/\r\n|\r|\n/).length),
        [content]
    );

    async function loadContent() {
        try {
            setLoading(true);
            setError("");
            setErrorStatus(null);

            const result = await getEditableFileContent(item.rawId);
            const loadedContent = result.content ?? "";

            setContent(loadedContent);
            setOriginalContent(loadedContent);
            setChecksum(result.checksum || "");
            setContentType(result.contentType || item.type || "text/plain");
        } catch (err) {
            setError(err.message || "Failed to load file content");
            setErrorStatus(err.status || null);
        } finally {
            setLoading(false);
        }
    }

    useEffect(() => {
        loadContent();
    }, [item.rawId]);

    useEffect(() => {
        if (!loading && !error) {
            textareaRef.current?.focus();
        }
    }, [loading, error]);

    function requestClose() {
        if (saving) return;

        if (dirty && !window.confirm("Discard your unsaved changes?")) {
            return;
        }

        onClose?.();
    }

    async function saveContent() {
        if (loading || saving || !dirty) return;

        try {
            setSaving(true);
            setError("");
            setErrorStatus(null);

            const result = await updateEditableFileContent(
                item.rawId,
                content,
                checksum
            );

            const savedContent = result.content ?? content;

            setContent(savedContent);
            setOriginalContent(savedContent);
            setChecksum(result.checksum || checksum);
            setContentType(result.contentType || contentType);

            await onSaved?.(result);
        } catch (err) {
            setError(err.message || "Failed to save file content");
            setErrorStatus(err.status || null);
        } finally {
            setSaving(false);
        }
    }

    function handleEditorKeyDown(event) {
        if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") {
            event.preventDefault();
            saveContent();
            return;
        }

        if (event.key === "Tab") {
            event.preventDefault();

            const input = event.currentTarget;
            const start = input.selectionStart;
            const end = input.selectionEnd;
            const nextContent = `${content.slice(0, start)}    ${content.slice(end)}`;

            setContent(nextContent);

            requestAnimationFrame(() => {
                input.selectionStart = start + 4;
                input.selectionEnd = start + 4;
            });
        }
    }

    return (
        <div className="text-editor-modal-backdrop" onClick={requestClose}>
            <section
                className="text-editor-modal"
                role="dialog"
                aria-modal="true"
                aria-labelledby="text-editor-title"
                onClick={(event) => event.stopPropagation()}
            >
                <header className="text-editor-modal-header">
                    <div className="text-editor-modal-heading">
                        <h2 id="text-editor-title">{item.name}</h2>
                        <p>
                            {contentType}
                            {dirty ? " · Unsaved changes" : " · Up to date"}
                        </p>
                    </div>

                    <div className="file-viewer-actions">
                        <button
                            className="text-editor-modal-close"
                            type="button"
                            onClick={requestClose}
                            disabled={saving}
                            aria-label="Close editor"
                            title="Close"
                        >
                            ×
                        </button>
                    </div>
                </header>

                <div
                    className="text-editor-modal-body"
                    style={{
                        alignItems: "stretch",
                        justifyContent: "stretch",
                        flexDirection: "column",
                    }}
                >
                    {loading ? (
                        <div className="text-editor-modal-message">Loading file content...</div>
                    ) : (
                        <>
                            {error && (
                                <div
                                    role="alert"
                                    style={{
                                        display: "flex",
                                        alignItems: "center",
                                        justifyContent: "space-between",
                                        gap: "16px",
                                        padding: "12px 18px",
                                        background: "rgba(127, 29, 29, 0.28)",
                                        borderBottom: "1px solid rgba(248, 113, 113, 0.28)",
                                        color: "#fecaca",
                                        fontSize: "13px",
                                    }}
                                >
                                    <span>{error}</span>

                                    {errorStatus === 409 && (
                                        <button
                                            type="button"
                                            onClick={loadContent}
                                            style={{
                                                flexShrink: 0,
                                                border: "1px solid rgba(255, 255, 255, 0.14)",
                                                borderRadius: "8px",
                                                background: "#252525",
                                                color: "#ffffff",
                                                padding: "7px 11px",
                                                cursor: "pointer",
                                            }}
                                        >
                                            Reload latest
                                        </button>
                                    )}
                                </div>
                            )}

                            <textarea
                                ref={textareaRef}
                                className="text-editor-modal-textarea"
                                value={content}
                                onChange={(event) => setContent(event.target.value)}
                                onKeyDown={handleEditorKeyDown}
                                spellCheck={false}
                                aria-label={`Edit ${item.name}`}
                                style={{
                                    flex: 1,
                                    width: "100%",
                                    minHeight: 0,
                                    resize: "none",
                                    border: "none",
                                    outline: "none",
                                    boxSizing: "border-box",
                                    padding: "20px 22px",
                                    background: "#0b0e12",
                                    color: "#e5e7eb",
                                    caretColor: "#ffffff",
                                    fontFamily:
                                        'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace',
                                    fontSize: "14px",
                                    lineHeight: 1.65,
                                    tabSize: 4,
                                }}
                            />
                        </>
                    )}
                </div>

                <footer
                    style={{
                        minHeight: "64px",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                        gap: "16px",
                        padding: "10px 18px",
                        borderTop: "1px solid rgba(255, 255, 255, 0.08)",
                        background: "#17191d",
                    }}
                >
                    <span style={{ color: "#929292", fontSize: "12px" }}>
                        {lineCount} {lineCount === 1 ? "line" : "lines"} · {byteCount} bytes
                        {" · "}Ctrl/Cmd + S to save
                    </span>

                    <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                        <button
                            type="button"
                            onClick={requestClose}
                            disabled={saving}
                            style={{
                                height: "38px",
                                borderRadius: "10px",
                                border: "1px solid rgba(255, 255, 255, 0.1)",
                                background: "#252525",
                                color: "#ededed",
                                padding: "0 15px",
                                cursor: saving ? "not-allowed" : "pointer",
                            }}
                        >
                            Cancel
                        </button>

                        <button
                            type="button"
                            onClick={saveContent}
                            disabled={loading || saving || !dirty}
                            style={{
                                height: "38px",
                                borderRadius: "10px",
                                border: "1px solid rgba(96, 165, 250, 0.35)",
                                background:
                                    loading || saving || !dirty ? "#2a2d33" : "#374151",
                                color:
                                    loading || saving || !dirty ? "#777f8c" : "#ffffff",
                                padding: "0 17px",
                                cursor:
                                    loading || saving || !dirty
                                        ? "not-allowed"
                                        : "pointer",
                                fontWeight: 600,
                            }}
                        >
                            {saving ? "Saving..." : "Save changes"}
                        </button>
                    </div>
                </footer>
            </section>
        </div>
    );
}

export default TextEditorModal;