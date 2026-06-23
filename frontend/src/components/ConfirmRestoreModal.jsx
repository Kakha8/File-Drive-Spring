import "./ConfirmRestoreModal.css";

export default function ConfirmRestoreModal({
                                                open,
                                                count = 1,
                                                loading = false,
                                                onConfirm,
                                                onCancel,
                                            }) {
    if (!open) {
        return null;
    }

    return (
        <div className="restore-modal-backdrop">
            <div className="restore-modal">
                <div className="restore-modal-icon">↩</div>

                <h2>{loading ? "Restoring..." : "Restore from trash?"}</h2>

                <p>
                    You are about to restore{" "}
                    <strong>
                        {count} {count === 1 ? "item" : "items"}
                    </strong>{" "}
                    from trash.
                </p>

                <p className="restore-modal-muted">
                    Large folders may take a moment to restore.
                </p>

                {loading && (
                    <div className="restore-moving-indicator">
                        <span className="restore-spinner" />
                        <span>Restoring from trash. Please wait...</span>
                    </div>
                )}

                <div className="restore-modal-actions">
                    <button
                        type="button"
                        className="restore-cancel"
                        disabled={loading}
                        onClick={onCancel}
                    >
                        Cancel
                    </button>

                    <button
                        type="button"
                        className="restore-confirm"
                        disabled={loading}
                        onClick={onConfirm}
                    >
                        {loading ? "Restoring..." : "Restore"}
                    </button>
                </div>
            </div>
        </div>
    );
}