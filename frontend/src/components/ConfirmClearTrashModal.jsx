import { useMemo, useState } from "react";
import "./ConfirmClearTrashModal.css";

const REQUIRED_TEXT = "CLEAR TRASHCAN";

export default function ConfirmClearTrashModal({
                                                   open,
                                                   count,
                                                   loading = false,
                                                   onConfirm,
                                                   onCancel,
                                               }) {
    const [confirmText, setConfirmText] = useState("");

    const canConfirm = useMemo(() => {
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
        if (!canConfirm) {
            return;
        }

        await onConfirm();
        setConfirmText("");
    }

    return (
        <div className="clear-trash-backdrop" role="presentation">
            <div
                className="clear-trash-modal"
                role="dialog"
                aria-modal="true"
                aria-labelledby="clear-trash-title"
            >
                <div className="clear-trash-icon">
                    <svg
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2.2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        aria-hidden="true"
                    >
                        <path d="M3 6h18" />
                        <path d="M8 6V4h8v2" />
                        <path d="M6 6l1 15h10l1-15" />
                        <path d="M10 11l4 4" />
                        <path d="M14 11l-4 4" />
                    </svg>
                </div>

                <h2 id="clear-trash-title">Clear trash?</h2>

                <p>
                    This will permanently delete{" "}
                    <strong>
                        {count} {count === 1 ? "item" : "items"}
                    </strong>{" "}
                    from your trashcan. This action cannot be undone.
                </p>

                <p className="clear-trash-warning">
                    To confirm, type <strong>{REQUIRED_TEXT}</strong> below.
                </p>

                <input
                    value={confirmText}
                    onChange={(event) => setConfirmText(event.target.value)}
                    placeholder={REQUIRED_TEXT}
                    disabled={loading}
                    autoFocus
                />

                <div className="clear-trash-actions">
                    <button
                        type="button"
                        className="clear-trash-cancel"
                        onClick={handleCancel}
                        disabled={loading}
                    >
                        Cancel
                    </button>

                    <button
                        type="button"
                        className="clear-trash-confirm"
                        onClick={handleConfirm}
                        disabled={!canConfirm}
                    >
                        {loading ? "Clearing..." : "Clear trash"}
                    </button>
                </div>
            </div>
        </div>
    );
}