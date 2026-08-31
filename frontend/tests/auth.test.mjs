import test, { beforeEach, afterEach, mock } from 'node:test';
import assert from 'node:assert/strict';
import { login, verifyTotpLogin } from '../src/api/auth.js';
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

test('verification sends leading zeros and challenge, not password; stores session on success', async () => {
    const fetch = respond({ accessToken: 'verified-access' });
    await verifyTotpLogin('challenge', '001234');
    const [url, options] = fetch.mock.calls[0].arguments;
    assert.ok(url.endsWith('/api/auth/mfa/totp'));
    assert.equal(options.credentials, 'include');
    assert.deepEqual(JSON.parse(options.body), { challengeToken: 'challenge', code: '001234' });
    assert.equal(getAccessToken(), 'verified-access');
});

test('rejected MFA does not refresh or create a session and preserves HTTP status', async () => {
    const fetch = respond({ message: 'Invalid credentials' }, 401);
    await assert.rejects(verifyTotpLogin('challenge', '001234'), error => error.status === 401);
    assert.equal(fetch.mock.callCount(), 1);
    assert.equal(getAccessToken(), null);
});

test('throttling status is available to the UI', async () => {
    respond({ message: 'Too many attempts' }, 429);
    await assert.rejects(login('alice', 'password'), error => error.status === 429);
});

test('malformed or incomplete verification does not establish a session', async () => {
    const fetch = respond({ mfaRequired: true, accessToken: 'invalid' });
    await assert.rejects(verifyTotpLogin('challenge', '12345'), /six-digit/);
    await assert.rejects(verifyTotpLogin('challenge', 123456), /six-digit/);
    assert.equal(fetch.mock.callCount(), 0);
    await assert.rejects(verifyTotpLogin('challenge', '001234'), /has not completed/);
    assert.equal(getAccessToken(), null);
});

test('network failure and missing access token leave user unauthenticated', async () => {
    const fetch = respond({});
    await assert.rejects(verifyTotpLogin('challenge', '001234'), /access token/);
    fetch.mock.mockImplementation(async () => { throw new Error('Offline'); });
    await assert.rejects(login('alice', 'password'), /Offline/);
    assert.equal(getAccessToken(), null);
});
