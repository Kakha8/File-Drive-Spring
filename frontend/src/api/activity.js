import { apiFetch } from "./http";

async function readApiError(
    response,
    fallbackMessage
) {
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

export async function getRecentActivity({
                                            page = 0,
                                            size = 20,
                                            from = null,
                                            to = null,
                                            types = [],
                                        } = {}) {
    const params = new URLSearchParams({
        page: String(Math.max(0, page)),
        size: String(
            Math.min(
                Math.max(1, size),
                50
            )
        ),
    });

    if (from) {
        params.set("from", from);
    }

    if (to) {
        params.set("to", to);
    }

    for (const type of types) {
        if (type) {
            params.append("types", type);
        }
    }

    const response = await apiFetch(
        `/api/activity?${params.toString()}`
    );

    if (!response.ok) {
        throw new Error(
            await readApiError(
                response,
                "Failed to load recent activity"
            )
        );
    }

    return response.json();
}

export async function getActivityTypes() {
    const response = await apiFetch(
        "/api/activity/types"
    );

    if (!response.ok) {
        throw new Error(
            await readApiError(
                response,
                "Failed to load activity types"
            )
        );
    }

    const data = await response.json();

    return Array.isArray(data)
        ? data
        : [];
}