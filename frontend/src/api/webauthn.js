import { apiFetch } from './http';
import { requestCredential } from './webauthn-browser.js';

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
