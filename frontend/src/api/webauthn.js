import { apiFetch } from './http.js';
import { requestCredential } from './webauthn-browser.js';

export async function getSecurityKeyStatus() {
    const response = await apiFetch('/api/webauthn/credentials');
    if (!response.ok) throw new Error('Could not load registered security keys.');
    const data = await response.json();
    if (typeof data.enabled !== 'boolean' || !Array.isArray(data.devices)
        || data.devices.some(device => !Number.isInteger(device?.credentialRecordId)
            || typeof device.displayName !== 'string')) {
        throw new Error('The server returned an invalid security-key status.');
    }
    return data;
}

export async function removeSecurityKey(credentialRecordId, password, totpDeviceId, totpCode, onProgress) {
    async function post(path, body) {
        const response = await apiFetch(path, { method: 'POST',
            headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
        if (!response.ok) {
            const data = await response.json().catch(() => ({}));
            throw new Error(data.message || 'Could not remove the security key.');
        }
        return response.json();
    }
    const base = `/api/webauthn/credentials/${credentialRecordId}/removal`;
    const options = await post(`${base}/options`, { password, totpDeviceId, totpCode });
    let authorizationCredential = null;
    if (options.authorizationPublicKey) {
        onProgress('Confirm removal with a registered security key.');
        authorizationCredential = await requestCredential(options.authorizationPublicKey);
    }
    return post(`${base}/finish`, { requestId: options.requestId, authorizationCredential });
}

export async function registerSecurityKey(details, onProgress) {
    async function post(path, body) {
        const response = await apiFetch(path, { method: 'POST',
            headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
        if (!response.ok) {
            const data = await response.json().catch(() => ({}));
            throw new Error(data.message || 'Could not register the security key. Check your password and verification, then try again.');
        }
        return response.json();
    }
    const options = await post('/api/webauthn/registration/options', details);
    let authorizationCredential = null;
    if (options.authorizationPublicKey) {
        onProgress('Confirm with an existing security key first.');
        authorizationCredential = await requestCredential(options.authorizationPublicKey);
    }
    onProgress('Follow the browser prompt to register your new security key.');
    const credential = await requestCredential(options.publicKey, true);
    return post('/api/webauthn/registration/finish', { requestId: options.requestId, credential, authorizationCredential });
}
