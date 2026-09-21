import test, { beforeEach, afterEach, mock } from 'node:test';
import assert from 'node:assert/strict';
import { login } from '../src/api/auth.js';
import { clearAccessToken, getAccessToken, setAccessToken } from '../src/api/tokenstore.js';

beforeEach(() => clearAccessToken());
afterEach(() => mock.restoreAll());

function respond(body, status = 200) {
    return mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify(body), {
        status, headers: { 'Content-Type': 'application/json' },
    }));
}

test('password-only login keeps the existing session contract', async () => {
    respond({ accessToken: 'access' });
    assert.deepEqual(await login('alice', 'password'), { accessToken: 'access' });
    assert.equal(getAccessToken(), 'access');
});

test('MFA challenge never establishes a session, even if response contains a token', async () => {
    setAccessToken('old');
    const challenge = { mfaRequired: true, challengeToken: 'challenge', expiresAt: '2099-01-01T00:00:00Z' };
    respond({ ...challenge, accessToken: 'must-not-store' });
    assert.deepEqual(await login('alice', 'password'), challenge);
    assert.equal(getAccessToken(), null);
});

test('rejects malformed challenge response', async () => {
    respond({ mfaRequired: true, expiresAt: 'invalid' });
    await assert.rejects(login('alice', 'password'), /invalid sign-in challenge/);
    assert.equal(getAccessToken(), null);
});

test('throttling status is available to the UI', async () => {
    respond({ message: 'Too many attempts' }, 429);
    await assert.rejects(login('alice', 'password'), error => error.status === 429);
});

test('legacy TOTP challenges are rejected without establishing a session', async () => {
    respond({ mfaRequired: true, method: 'totp', challengeToken: 'challenge',
        expiresAt: '2099-01-01T00:00:00Z' });
    await assert.rejects(login('alice', 'password'), /Unsupported sign-in verification method/);
    assert.equal(getAccessToken(), null);
});

test('network failure leaves user unauthenticated', async () => {
    const fetch = respond({ accessToken: 'unused' });
    fetch.mock.mockImplementation(async () => { throw new Error('Offline'); });
    await assert.rejects(login('alice', 'password'), /Offline/);
    assert.equal(getAccessToken(), null);
});
