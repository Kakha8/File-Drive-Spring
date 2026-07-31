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