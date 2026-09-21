import test, { afterEach, mock } from 'node:test';
import assert from 'node:assert/strict';
import { prepareOptions, serializeCredential } from '../src/api/webauthn-browser.js';
import { login, verifyWebAuthnLogin } from '../src/api/auth.js';
import { getSecurityKeyStatus, removeSecurityKey } from '../src/api/webauthn.js';
import { clearAccessToken, getAccessToken } from '../src/api/tokenstore.js';

afterEach(() => { mock.restoreAll(); clearAccessToken(); });

test('creation options decode binary IDs without mutating server options', () => {
    const source = { challenge: '-_8', user: { id: 'AAE', name: 'alice' }, excludeCredentials: [{ id: 'AgM', type: 'public-key' }] };
    const result = prepareOptions(source, true);
    assert.deepEqual([...result.challenge], [251, 255]);
    assert.deepEqual([...result.user.id], [0, 1]);
    assert.deepEqual([...result.excludeCredentials[0].id], [2, 3]);
    assert.equal(source.user.id, 'AAE');
});

test('assertion serialization preserves signature bytes and absent user handle', () => {
    const bytes = new Uint8Array([251, 255]).buffer;
    const result = serializeCredential({ id: '-_8', rawId: bytes, type: 'public-key',
        response: { clientDataJSON: bytes, authenticatorData: bytes, signature: bytes, userHandle: null },
        getClientExtensionResults: () => ({}) });
    assert.equal(result.response.signature, '-_8');
    assert.equal(result.response.userHandle, null);
    assert.equal(result.rawId, '-_8');
});

test('WebAuthn challenge preserves its method without creating a session', async () => {
    mock.method(globalThis, 'fetch', async () => Response.json({ mfaRequired: true, method: 'webauthn',
        challengeToken: 'challenge', expiresAt: '2099-01-01T00:00:00Z', accessToken: 'ignore' }));
    assert.equal((await login('alice', 'password')).method, 'webauthn');
    assert.equal(getAccessToken(), null);
});

test('WebAuthn option rejection never attempts refresh or establishes a session', async () => {
    const request = mock.method(globalThis, 'fetch', async () => Response.json({ message: 'Expired' }, { status: 401 }));
    await assert.rejects(verifyWebAuthnLogin('challenge'), error => error.status === 401);
    assert.equal(request.mock.callCount(), 1);
    assert.equal(getAccessToken(), null);
});

test('security-key status returns registered device metadata', async () => {
    const expected = { enabled: true, devices: [{ credentialRecordId: 7, displayName: 'Enigma Wallet',
        createdAt: '2026-09-15T10:00:00Z', lastUsedAt: null }] };
    mock.method(globalThis, 'fetch', async () => Response.json(expected));
    assert.deepEqual(await getSecurityKeyStatus(), expected);
});

test('security-key removal no longer sends legacy TOTP credentials', async () => {
    const requests = [];
    mock.method(globalThis, 'fetch', async (_url, options) => {
        requests.push(JSON.parse(options.body));
        return requests.length === 1
            ? Response.json({ requestId: 'request-1', authorizationPublicKey: null })
            : Response.json({ removedCredentialRecordId: 7, enabled: false, remainingDevices: 0 });
    });
    const result = await removeSecurityKey(7, 'password', () => assert.fail());
    assert.equal(result.remainingDevices, 0);
    assert.deepEqual(requests[0], { password: 'password' });
    assert.deepEqual(requests[1], { requestId: 'request-1', authorizationCredential: null });
});
