import React from "react";
import "./ConfirmTrashModal.css";


export default function ConfirmTrashModal({
                                              open,
                                              fileName,
                                              count = 1,
                                              loading = false,
                                              onConfirm,
                                              onCancel,
                                          }) {
    if (!open) {
        return null;
    }

    return (
        <div className="modal-overlay">
            <div className="trash-modal">
                <div className="trash-modal-header">
                    <h2>{loading ? "Moving to trash..." : "Move to trash?"}</h2>
                </div>

                <div className="trash-modal-body">
                    <p>
                        You are about to move{" "}
                        <span className="file-name">
                            {count > 1 ? `${count} items` : fileName}
                        </span>{" "}
                        to trash.
                    </p>

                    <p className="modal-subtext">
                        Large files or folders may take a moment to move.
                    </p>

                    {loading && (
                        <div className="trash-moving-indicator">
                            <span className="trash-spinner" />
                            <span>Moving to trash. Please wait...</span>
                        </div>
                    )}
                </div>

                <div className="trash-modal-actions">
                    <button
                        type="button"
                        className="btn btn-secondary"
                        disabled={loading}
                        onClick={onCancel}
                    >
                        Cancel
                    </button>

                    <button
                        type="button"
                        className="btn btn-danger"
                        disabled={loading}
                        onClick={onConfirm}
                    >
                        {loading ? "Moving..." : "Move to trash"}
                    </button>
                </div>
            </div>
        </div>
    );
}