import { apiFetch } from "./http";

export async function getAdminStorageUsage() {
    const response = await apiFetch("/api/admin/storage");
    if (!response.ok) {
        throw new Error("Failed to load storage usage");
    }
    return response.json();
}

export async function getAdminLogs({ page = 0, size = 10, all = false, users = [], actions = [], from = "", to = "" } = {}) {
    const params = new URLSearchParams({
        page: String(Math.max(0, page)),
        size: String(Math.min(Math.max(1, size), 50)),
        all: String(all),
    });
    users.forEach((username) => params.append("users", username));
    actions.forEach((action) => params.append("actions", action));
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    const response = await apiFetch(`/api/admin/logs?${params.toString()}`);
    if (!response.ok) {
        throw new Error("Failed to load logs");
    }
    return response.json();
}

export async function getAdminLogActions() {
    const response = await apiFetch("/api/admin/logs/actions");
    if (!response.ok) {
        throw new Error("Failed to load log actions");
    }
    return response.json();
}
