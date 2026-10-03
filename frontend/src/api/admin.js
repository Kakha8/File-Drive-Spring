import { apiFetch } from "./http";

export async function getAdminStorageUsage() {
    const response = await apiFetch("/api/admin/storage");
    if (!response.ok) {
        throw new Error("Failed to load storage usage");
    }
    return response.json();
}
