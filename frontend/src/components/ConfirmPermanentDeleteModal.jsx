import { useMemo, useState } from "react";
import "./ConfirmPermanentDeleteModal.css";

const REQUIRED_TEXT = "DELETE PERMANENTLY";

export default function ConfirmPermanentDeleteModal({
                                                        open,
                                                        count,
                                                        onConfirm,
                                                        onCancel,
                                                        loading = false,
                                                    }) {
    const [confirmText, setConfirmText] = useState("");

    const canDelete = useMemo(() => {
        return confirmText === REQUIRED_TEXT && !loading;
    }, [confirmText, loading]);

    if (!open) {
        return null;
    }

    function handleCancel() {
        if (loading) {
            return;
        }

        setConfirmText("");
        onCancel();
    }

    async function handleConfirm() {
        if (!canDelete) {
            return;
        }

        await onConfirm();
        setConfirmText("");
    }

    return (
        <div className="permanent-delete-backdrop" role="presentation">
            <div
                className="permanent-delete-modal"
                role="dialog"
                aria-modal="true"
                aria-labelledby="permanent-delete-title"
            >
                <div className="permanent-delete-icon">!</div>

                <h2 id="permanent-delete-title">Permanently delete items?</h2>

                <p>
                    This will permanently delete{" "}
                    <strong>
                        {count} {count === 1 ? "item" : "items"}
                    </strong>{" "}
                    from trash. This action cannot be undone.
                </p>

                <p className="permanent-delete-warning">
                    To confirm, type <strong>{REQUIRED_TEXT}</strong> below.
                </p>

                <input
                    value={confirmText}
                    onChange={(event) => setConfirmText(event.target.value)}
                    placeholder={REQUIRED_TEXT}
                    disabled={loading}
                    autoFocus
                />

                <div className="permanent-delete-actions">
                    <button
                        type="button"
                        className="permanent-delete-cancel"
                        onClick={handleCancel}
                        disabled={loading}
                    >
                        Cancel
                    </button>

                    <button
                        type="button"
                        className="permanent-delete-confirm"
                        onClick={handleConfirm}
                        disabled={!canDelete}
                    >
                        {loading ? "Deleting..." : "Delete permanently"}
                    </button>
                </div>
            </div>
        </div>
    );
}