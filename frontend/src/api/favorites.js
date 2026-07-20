import { apiFetch } from "./http";

async function readApiError(response, fallbackMessage) {
    try {
        const data = await response.clone().json();
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

export async function getFavorites() {
    const response = await apiFetch("/api/favorites");

    if (!response.ok) {
        throw new Error(
            await readApiError(response, "Failed to load favorites")
        );
    }

    return response.json();
}

export async function addToFavorites(entityType, entityId) {
    const response = await apiFetch("/api/favorites/add", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify({
            fileIds: entityType === "FILE" ? [entityId] : [],
            folderIds: entityType === "FOLDER" ? [entityId] : [],
        }),
    });

    if (!response.ok) {
        throw new Error(
            await readApiError(response, "Failed to add favorite")
        );
    }
}

export async function removeFromFavorites(favoriteId) {
    const response = await apiFetch(
        `/api/favorites/${favoriteId}`,
        {
            method: "DELETE",
        }
    );

    if (!response.ok) {
        throw new Error(
            await readApiError(response, "Failed to remove favorite")
        );
    }
}