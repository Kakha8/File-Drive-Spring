import { apiFetch } from "./http";

export async function getTotpStatus() {
    const response = await apiFetch("/api/mfa/totp/enrollments/status");
    if (!response.ok) {
        throw new Error("Could not load two-factor authentication status.");
    }
    const data = await response.json();
    if (typeof data.enabled !== "boolean" || !Array.isArray(data.devices)
        || data.devices.some((device) => typeof device?.displayName !== "string")) {
        throw new Error("The server returned an invalid two-factor authentication status.");
    }
    return { enabled: data.enabled, devices: data.devices };
}
