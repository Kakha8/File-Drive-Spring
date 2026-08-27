import { apiFetch } from "./http";

const DEVICE_ID_STORAGE_KEY = "lockbox-device-id";

function getDeviceId() {
    const saved = localStorage.getItem(DEVICE_ID_STORAGE_KEY);

    if (saved) return saved;

    const deviceId = crypto.randomUUID();
    localStorage.setItem(DEVICE_ID_STORAGE_KEY, deviceId);
    return deviceId;
}

export async function getLockboxStatus() {
    const response = await apiFetch(
        `/api/lockbox/enrollments/status?deviceId=${encodeURIComponent(getDeviceId())}`
    );

    if (!response.ok) {
        throw new Error("Failed to check Lockbox status");
    }

    return response.json();
}

export async function getRegisteredDevices() {
    const response = await apiFetch("/api/lockbox/devices");

    if (!response.ok) {
        throw new Error("Failed to load registered devices");
    }

    const result = await response.json();
    return Array.isArray(result.devices) ? result.devices : [];
}
