import { apiFetch } from "./http";

async function readApiError(response, fallbackMessage) {
    try {
        const data = await response.clone().json();

        return (
            data?.message ||
            data?.error ||
            fallbackMessage
        );
    } catch {
        try {
            const text = await response.text();
            return text || fallbackMessage;
        } catch {
            return fallbackMessage;
        }
    }
}

export async function getNotifications({
    page = 0,
    size = 10,
} = {}) {
    const params = new URLSearchParams({
        page: String(Math.max(0, page)),
        size: String(Math.max(1, size)),
    });

    const response = await apiFetch(
        `/api/notifications?${params.toString()}`
    );

    if (!response.ok) {
        throw new Error(
            await readApiError(
                response,
                "Failed to load notifications"
            )
        );
    }

    return response.json();
}

export async function getUnreadNotificationCount() {
    const response = await apiFetch(
        "/api/notifications/unread-count"
    );

    if (!response.ok) {
        throw new Error(
            await readApiError(
                response,
                "Failed to load unread notification count"
            )
        );
    }

    const data = await response.json();

    return Number(data?.unreadCount || 0);
}

export async function markNotificationRead(notificationId) {
    const response = await apiFetch(
        `/api/notifications/${notificationId}/read`,
        {
            method: "POST",
        }
    );

    if (!response.ok) {
        throw new Error(
            await readApiError(
                response,
                "Failed to mark notification as read"
            )
        );
    }

    return response.json();
}

export async function markAllNotificationsRead() {
    const response = await apiFetch(
        "/api/notifications/read-all",
        {
            method: "POST",
        }
    );

    if (!response.ok) {
        throw new Error(
            await readApiError(
                response,
                "Failed to mark all notifications as read"
            )
        );
    }

    return response.json();
}
