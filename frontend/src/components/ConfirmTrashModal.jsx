import React from "react";
import "./ConfirmTrashModal.css";

export default function ConfirmTrashModal({
                                              open,
                                              fileName,
                                              onConfirm,
                                              onCancel,
                                          }) {
    if (!open) return null;

    return (
        <div className="modal-overlay">
            <div className="trash-modal">
                <div className="trash-modal-header">
                    <h2>Move to trash?</h2>
                </div>

                <div className="trash-modal-body">
                    <p>
                        Are you sure you want to move{" "}
                        <span className="file-name">"{fileName}"</span> to trash?
                    </p>
                    <p className="modal-subtext">
                        You can restore it later from Trash.
                    </p>
                </div>

                <div className="trash-modal-actions">
                    <button className="btn btn-secondary" onClick={onCancel}>
                        Cancel
                    </button>
                    <button className="btn btn-danger" onClick={onConfirm}>
                        Move to trash
                    </button>
                </div>
            </div>
        </div>
    );
}