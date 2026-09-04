import { apiFetch } from "./http";

export async function getTotpStatus() {
    const response = await apiFetch("/api/mfa/totp/enrollments/status");
    if (!response.ok) {
        throw new Error("Could not load two-factor authentication status.");
    }
    const data = await response.json();
    if (typeof data.enabled !== "boolean" || !Array.isArray(data.devices)
        || data.devices.some((device) => !Number.isInteger(device?.deviceId)
            || typeof device.displayName !== "string")) {
        throw new Error("The server returned an invalid two-factor authentication status.");
    }
    return { enabled: data.enabled, devices: data.devices };
}

export async function removeTotpDevice(deviceId, password, authorizingDeviceId, code) {
    const response = await apiFetch(`/api/mfa/totp/enrollments/devices/${deviceId}`, {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ password, authorizingDeviceId, code }),
    });
    if (!response.ok) {
        let message = "Could not remove the device.";
        try { message = (await response.json()).message || message; } catch { /* use fallback */ }
        throw new Error(message);
    }
    return response.json();
}
