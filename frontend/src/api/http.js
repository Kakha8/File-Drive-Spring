import { refresh } from "./auth";
import {
    clearAccessToken,
    getAccessToken,
} from "./tokenstore";
import { API_BASE_URL } from "./config";

function createAuthError(
    message = "Session expired. Please log in again."
) {
    const error = new Error(message);
    error.name = "AuthError";
    return error;
}

export async function apiFetch(path, options = {}) {
    const doFetch = (token) =>
        fetch(`${API_BASE_URL}${path}`, {
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
