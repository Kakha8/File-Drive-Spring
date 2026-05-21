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

function uploadFileOnce(parentId, file, token, onProgress) {
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

            reject({
                status: xhr.status,
                message:
                    xhr.responseText ||
                    `Failed to upload file. Server returned ${xhr.status}`,
            });
        };

        xhr.onerror = () => {
            reject({
                status: 0,
                message: "Network error while uploading file",
            });
        };

        const formData = new FormData();
        formData.append("file", file);
        formData.append("parentId", String(parentId));

        xhr.send(formData);
    });
}

export async function uploadFile(parentId, file, onProgress) {
    let token = getAccessToken();

    if (!token) {
        const result = await refresh();
        token = result.accessToken;
    }

    try {
        return await uploadFileOnce(parentId, file, token, onProgress);
    } catch (err) {
        if (err.status !== 401) {
            throw new Error(err.message || "Failed to upload file");
        }

        const result = await refresh();

        return uploadFileOnce(parentId, file, result.accessToken, onProgress);
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