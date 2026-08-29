import {
    clearAccessToken,
    getAccessToken,
    setAccessToken,
} from "./tokenstore";
import { API_BASE_URL } from "./config";

let refreshPromise = null;

/**
 * Signs the user in and stores the returned access token in memory.
 */
export async function login(username, password) {
    const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
        method: "POST",
        credentials: "include",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify({
            username,
            password,
        }),
    });

    if (!response.ok) {
        throw new Error(
            await readApiError(response, "Login failed")
        );
    }

    const data = await response.json();
    const token = data.accessToken || data.token;

    if (!token) {
        throw new Error("Login response did not include an access token");
    }

    setAccessToken(token);

    return {
        accessToken: token,
    };
}

/**
 * Requests a new access token using the refresh-token cookie.
 */
async function doRefresh() {
    const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
        method: "POST",
        credentials: "include",
    });

    if (!response.ok) {
        clearAccessToken();

        throw new Error(
            await readApiError(response, "Refresh failed")
        );
    }

    const data = await response.json();
    const token = data.accessToken || data.token;

    if (!token) {
        clearAccessToken();
        throw new Error("Refresh response did not include an access token");
    }

    setAccessToken(token);

    return {
        accessToken: token,
    };
}

/**
 * Prevents multiple simultaneous refresh requests.
 */
export async function refresh() {
    if (!refreshPromise) {
        refreshPromise = doRefresh().finally(() => {
            refreshPromise = null;
        });
    }

    return refreshPromise;
}

/**
 * Signs the user out locally and asks the backend to remove
 * the refresh-token cookie.
 */
export async function logout() {
    clearAccessToken();

    try {
        await fetch(`${API_BASE_URL}/api/auth/logout`, {
            method: "POST",
            credentials: "include",
        });
    } catch {
        // The local access token is already removed, so the user
        // is still signed out even if the server request fails.
    }
}

/**
 * Safely decodes the access-token payload.
 *
 * This only reads display information from the token.
 * Authentication and authorization must still be enforced by the backend.
 */
function getAccessTokenPayload() {
    const token = getAccessToken();

    if (!token) {
        return null;
    }

    try {
        const parts = token.split(".");

        if (parts.length !== 3 || !parts[1]) {
            return null;
        }

        const normalizedPayload = parts[1]
            .replace(/-/g, "+")
            .replace(/_/g, "/");

        const paddedPayload = normalizedPayload.padEnd(
            Math.ceil(normalizedPayload.length / 4) * 4,
            "="
        );

        const binaryPayload = atob(paddedPayload);

        const payloadBytes = Uint8Array.from(
            binaryPayload,
            (character) => character.charCodeAt(0)
        );

        const decodedPayload = new TextDecoder().decode(payloadBytes);

        return JSON.parse(decodedPayload);
    } catch {
        return null;
    }
}

/**
 * Returns the signed-in username stored in the JWT subject.
 */
export function getCurrentUsername() {
    const payload = getAccessTokenPayload();

    return payload?.sub || "User";
}

/**
 * Returns all roles stored in the JWT.
 *
 * Your Spring backend creates a "roles" array such as:
 * ["ROLE_ADMIN"]
 */
export function getUserRoles() {
    const payload = getAccessTokenPayload();

    if (Array.isArray(payload?.roles)) {
        return payload.roles;
    }

    if (payload?.role) {
        return [payload.role];
    }

    return [];
}

/**
 * Preserves the original single-role helper.
 */
export function getUserRole() {
    return getUserRoles()[0] || null;
}

/**
 * Checks whether the signed-in user has a particular role.
 *
 * Both "ADMIN" and "ROLE_ADMIN" are accepted.
 */
export function hasRole(role) {
    if (!role) {
        return false;
    }

    const normalizedRole = role.startsWith("ROLE_")
        ? role
        : `ROLE_${role}`;

    return getUserRoles().includes(normalizedRole);
}

/**
 * Reads a useful error message from an API response.
 */
async function readApiError(response, fallbackMessage) {
    try {
        const text = await response.text();

        if (!text) {
            return fallbackMessage;
        }

        try {
            const data = JSON.parse(text);

            return (
                data.message ||
                data.error ||
                fallbackMessage
            );
        } catch {
            return text;
        }
    } catch {
        return fallbackMessage;
    }
}
