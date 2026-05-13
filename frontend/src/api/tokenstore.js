let accessToken = null;

const listeners = new Set();

function emit() {
    for (const listener of listeners) {
        listener(accessToken);
    }
}

export function getAccessToken() {
    return accessToken;
}

export function setAccessToken(token) {
    accessToken = token || null;
    emit();
}

export function clearAccessToken() {
    accessToken = null;
    emit();
}

export function subscribeAccessToken(listener) {
    listeners.add(listener);
    return () => listeners.delete(listener);
}