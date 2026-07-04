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

export async function searchUsers(query) {
    const response = await apiFetch(
        `/api/users/search?q=${encodeURIComponent(query)}`
    );

    if (!response.ok) {
        throw new Error(await readApiError(response, "Failed to search users"));
    }

    return response.json();
}