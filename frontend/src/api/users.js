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

export async function getAdminUsers() {
    const response = await apiFetch("/api/users");

    if (!response.ok) {
        throw new Error(await readApiError(response, "Failed to load users"));
    }

    return response.json();
}

export async function createAdminUser({ username, password, role, passwordChangeRequired }) {
    const response = await apiFetch("/api/register/as-admin", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify({ username, password, role, passwordChangeRequired }),
    });

    if (!response.ok) {
        throw new Error(await readApiError(response, "Failed to create user"));
    }

    return response.json();
}

export async function deleteAdminUser(id) {
    const response = await apiFetch(`/api/users/${id}`, {
        method: "DELETE",
    });

    if (!response.ok) {
        throw new Error(await readApiError(response, "Failed to delete user"));
    }
}
