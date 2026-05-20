import { refresh } from "./auth";
import { clearAccessToken, getAccessToken } from "./tokenStore";

const API_BASE = "https://localhost:8443";

export async function apiFetch(path, options = {}) {
    const doFetch = (token) =>
        fetch(`${API_BASE}${path}`, {
            ...options,
            credentials: "include",
            headers: {
                ...(options.headers || {}),
                ...(token ? { Authorization: `Bearer ${token}` } : {}),
            },
        });

    let response = await doFetch(getAccessToken());

    if (response.status !== 401) {
        return response;
    }

    const result = await refresh();
    response = await doFetch(result.accessToken);

    if (response.status === 401) {
        clearAccessToken();
        throw new Error("Session expired. Please log in again.");
    }

    return response;
}