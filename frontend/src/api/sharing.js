import { apiFetch } from "./http";

async function readApiError(response, fallbackMessage) {
    try {
        const data = await response.json();
        return data.message || data.error || fallbackMessage;
    } catch {
        try {
            const text = await response.text();
            return text || fallbackMessage;
        } catch {
            return fallbackMessage;
        }
    }
}

async function shareFileWithUser(resourceId, share) {
    const response = await apiFetch("/api/share/files", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify({
            fileIds: [resourceId],
            username: share.username,
            role: share.role,
        }),
    });

    if (!response.ok) {
        throw new Error(await readApiError(response, "Failed to share file"));
    }

    return response.json();
}

async function shareFolderWithUser(resourceId, share) {
    const response = await apiFetch("/api/share/folders", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify({
            folderIds: [resourceId],
            username: share.username,
            role: share.role,
        }),
    });

    if (!response.ok) {
        throw new Error(await readApiError(response, "Failed to share folder"));
    }

    return response.json();
}

export async function shareResource(payload) {
    const requests = payload.shares.map((share) => {
        if (payload.resourceType === "FILE") {
            return shareFileWithUser(payload.resourceId, share);
        }

        if (payload.resourceType === "FOLDER") {
            return shareFolderWithUser(payload.resourceId, share);
        }

        throw new Error(`Invalid resource type: ${payload.resourceType}`);
    });

    return Promise.all(requests);
}

export async function getSharedWithMe() {
    const response = await apiFetch("/api/share/with-me");

    if (!response.ok) {
        throw new Error(await readApiError(response, "Failed to load shared items"));
    }

    return response.json();
}