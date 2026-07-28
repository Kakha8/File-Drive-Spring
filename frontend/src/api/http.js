import { refresh } from "./auth";
import {
    clearAccessToken,
    getAccessToken,
} from "./tokenstore";

const API_BASE = "https://localhost:8443";

function createAuthError(
    message = "Session expired. Please log in again."
) {
    const error = new Error(message);
    error.name = "AuthError";
    return error;
}

export async function apiFetch(path, options = {}) {
    const doFetch = (token) =>
        fetch(`${API_BASE}${path}`, {
            ...options,
            credentials: "include",
            headers: {
                ...(options.headers || {}),
                ...(token
                    ? {
                        Authorization: `Bearer ${token}`,
                    }
                    : {}),
            },
        });

    let response = await doFetch(getAccessToken());

    if (response.status !== 401) {
        return response;
    }

    let refreshResult;

    try {
        refreshResult = await refresh();
    } catch {
        clearAccessToken();
        throw createAuthError();
    }

    response = await doFetch(refreshResult.accessToken);

    if (response.status === 401) {
        clearAccessToken();
        throw createAuthError();
    }

    return response;
}