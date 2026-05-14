import { apiFetch } from "./http";

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