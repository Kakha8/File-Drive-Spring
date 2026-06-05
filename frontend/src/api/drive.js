import { apiFetch } from "./http";
import { refresh } from "./auth";
import { getAccessToken } from "./tokenStore";

const API_BASE_URL = "https://localhost:8443";
export async function getRootFolder() {
    const response = await apiFetch("/api/folders/root");

    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Failed to load root folder");
    }

    return response.json();
}

export async function getFolder(folderId) {
    const response = await apiFetch(`/api/folders/${folderId}`);

    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Failed to load folder");
    }

    return response.json();
}

export async function getFileBlob(fileId) {
    const response = await apiFetch(`/api/files/${fileId}`);

    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Failed to open file");
    }

    return response.blob();
}

export async function getFolderZipBlob(folderId) {
    const response = await apiFetch(`/api/folders/${folderId}/download`);

    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Failed to download folder");
    }

    return response.blob();
}

export async function createFolder(parentId, name) {
    const response = await apiFetch("/api/folders", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify({
            name,
            parentId,
        }),
    });

    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Failed to create folder");
    }

    const text = await response.text();
    return text ? JSON.parse(text) : null;
}

export async function getFilesZipBlob(fileIds) {
    const response = await apiFetch("/api/files/download-zip", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify({
            fileIds,
        }),
    });

    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Failed to download selected files");
    }

    return response.blob();
}

export async function getMixedZipBlob(fileIds, folderIds) {
    const response = await apiFetch("/api/download/zip", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify({
            fileIds,
            folderIds,
        }),
    });

    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Failed to download selected items");
    }

    return response.blob();
}

export async function cancelUpload(uploadId) {
    const response = await apiFetch(`/api/uploads/${encodeURIComponent(uploadId)}`, {
        method: "DELETE",
    });

    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Failed to cancel upload");
    }
}

function uploadFileOnce(parentId, file, token, onProgress, signal, uploadId) {
    return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();

        xhr.open("POST", `${API_BASE_URL}/api/files`);
        xhr.withCredentials = true;

        if (token) {
            xhr.setRequestHeader("Authorization", `Bearer ${token}`);
        }

        xhr.upload.onloadstart = () => {
            onProgress?.(1);
        };

        xhr.upload.onprogress = (event) => {
            if (!event.lengthComputable) return;

            const percent = Math.round((event.loaded / event.total) * 100);
            onProgress?.(percent);
        };

        xhr.onload = () => {
            if (xhr.status >= 200 && xhr.status < 300) {
                try {
                    resolve(xhr.responseText ? JSON.parse(xhr.responseText) : null);
                } catch {
                    resolve(null);
                }

                return;
            }

            let parsedError = null;

            try {
                parsedError = xhr.responseText ? JSON.parse(xhr.responseText) : null;
            } catch {
                parsedError = null;
            }

            reject({
                status: xhr.status,
                error: parsedError?.error,
                message:
                    parsedError?.message ||
                    xhr.responseText ||
                    `Failed to upload file. Server returned ${xhr.status}`,
            });
        };

        xhr.onerror = () => {
            reject({
                status: 0,
                error: "NETWORK_ERROR",
                message: "Network error while uploading file",
            });
        };

        xhr.onabort = () => {
            reject({
                status: 0,
                error: "UPLOAD_CANCELED",
                name: "AbortError",
                message: "Upload canceled",
            });
        };

        if (signal) {
            if (signal.aborted) {
                xhr.abort();
                return;
            }

            signal.addEventListener(
                "abort",
                () => {
                    xhr.abort();
                },
                { once: true }
            );
        }

        const formData = new FormData();
        formData.append("file", file);
        formData.append("parentId", String(parentId));
        formData.append("uploadId", uploadId);

        xhr.send(formData);
    });
}

export async function uploadFile(parentId, file, onProgress, signal, uploadId) {
    let token = getAccessToken();

    if (!token) {
        const result = await refresh();
        token = result.accessToken;
    }

    try {
        return await uploadFileOnce(
            parentId,
            file,
            token,
            onProgress,
            signal,
            uploadId
        );
    } catch (err) {
        if (err?.name === "AbortError" || err?.error === "UPLOAD_CANCELED") {
            const error = new Error("Upload canceled");
            error.code = "UPLOAD_CANCELED";
            error.status = err.status;
            throw error;
        }

        if (err.status !== 401) {
            const error = new Error(err.message || "Failed to upload file");
            error.code = err.error;
            error.status = err.status;
            throw error;
        }

        const result = await refresh();

        try {
            return await uploadFileOnce(
                parentId,
                file,
                result.accessToken,
                onProgress,
                signal,
                uploadId
            );
        } catch (retryErr) {
            if (
                retryErr?.name === "AbortError" ||
                retryErr?.error === "UPLOAD_CANCELED"
            ) {
                const error = new Error("Upload canceled");
                error.code = "UPLOAD_CANCELED";
                error.status = retryErr.status;
                throw error;
            }

            const error = new Error(retryErr.message || "Failed to upload file");
            error.code = retryErr.error;
            error.status = retryErr.status;
            throw error;
        }
    }
}

export async function renameFile(fileId, newName) {
    const response = await apiFetch(`/api/files/${fileId}/rename`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify({ newName }),
    });

    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Failed to rename file");
    }
}

export async function renameFolder(folderId, newName) {
    const response = await apiFetch(`/api/folders/${folderId}/rename`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify({ newName }),
    });

    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Failed to rename folder");
    }
}

export async function deleteFile(fileId) {
    const response = await apiFetch(`/api/files/${fileId}`, {
        method: "DELETE",
    });

    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Failed to delete file");
    }
}

export async function deleteFolder(folderId) {
    const response = await apiFetch(`/api/folders/${folderId}`, {
        method: "DELETE",
    });

    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Failed to delete folder");
    }
}

export async function moveToTrash(fileIds = [], folderIds = []) {
    const response = await apiFetch("/api/trashcan/move", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify({
            fileIds,
            folderIds,
        }),
    });

    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Failed to move selected items to trash");
    }
}