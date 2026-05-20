import { clearAccessToken, getAccessToken, setAccessToken } from "./tokenStore";

const API_BASE = "https://localhost:8443";

let refreshPromise = null;

export async function login(username, password) {
    const response = await fetch(`${API_BASE}/api/auth/login`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({
            username,
            password,
        }),
    });

    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Login failed");
    }

    const data = await response.json();
    const token = data.accessToken || data.token;

    if (!token) {
        throw new Error("Login response missing access token");
    }

    setAccessToken(token);
    return { accessToken: token };
}

async function doRefresh() {
    const response = await fetch(`${API_BASE}/api/auth/refresh`, {
        method: "POST",
        credentials: "include",
    });

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
        clearAccessToken();
        throw new Error(data.message || "Refresh failed");
    }

    const token = data.accessToken || data.token;

    if (!token) {
        clearAccessToken();
        throw new Error("Refresh response missing access token");
    }

    setAccessToken(token);
    return { accessToken: token };
}

export async function refresh() {
    if (!refreshPromise) {
        refreshPromise = doRefresh().finally(() => {
            refreshPromise = null;
        });
    }

    return refreshPromise;
}

export async function logout() {
    clearAccessToken();

    await fetch(`${API_BASE}/api/auth/logout`, {
        method: "POST",
        credentials: "include",
    }).catch(() => {});
}

export function getUserRole() {
    const token = getAccessToken();

    if (!token) {
        return null;
    }

    try {
        const payload = JSON.parse(atob(token.split(".")[1]));
        return payload.role || null;
    } catch {
        return null;
    }
}