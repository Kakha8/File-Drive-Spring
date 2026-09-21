export function decodeBase64url(value) {
    const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
    return Uint8Array.from(atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')), c => c.charCodeAt(0));
}

export function encodeBase64url(value) {
    return btoa(Array.from(new Uint8Array(value), byte => String.fromCharCode(byte)).join(''))
        .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function prepareOptions(options, creation = false) {
    const result = { ...options, challenge: decodeBase64url(options.challenge) };
    if (creation) result.user = { ...options.user, id: decodeBase64url(options.user.id) };
    for (const field of ['allowCredentials', 'excludeCredentials']) {
        if (options[field]) result[field] = options[field].map(item => ({ ...item, id: decodeBase64url(item.id) }));
    }
    return result;
}

export function serializeCredential(credential) {
    if (!credential) throw new Error('Security-key verification was cancelled. Please try again.');
    const response = {};
    for (const field of ['clientDataJSON', 'attestationObject', 'authenticatorData', 'signature', 'userHandle']) {
        if (field in credential.response) response[field] = credential.response[field] == null
            ? null : encodeBase64url(credential.response[field]);
    }
    if (credential.response.getTransports) response.transports = credential.response.getTransports();
    return { id: credential.id, rawId: encodeBase64url(credential.rawId), type: credential.type,
        response, clientExtensionResults: credential.getClientExtensionResults() };
}

export async function requestCredential(options, creation = false) {
    if (!globalThis.isSecureContext || !globalThis.PublicKeyCredential || !navigator.credentials) {
        throw new Error('Security keys require a supported browser on HTTPS or localhost.');
    }
    try {
        const request = { publicKey: prepareOptions(options, creation) };
        return serializeCredential(await navigator.credentials[creation ? 'create' : 'get'](request));
    } catch (error) {
        if (error.name === 'NotAllowedError' || error.name === 'AbortError') {
            throw new Error('Security-key verification was cancelled or timed out. Please try again.', { cause: error });
        }
        if (error.name === 'InvalidStateError') throw new Error('This security key is already registered. Try another key.', { cause: error });
        throw error;
    }
}
