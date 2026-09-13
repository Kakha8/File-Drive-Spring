import { useRef, useState } from 'react';
import { registerSecurityKey } from '../api/webauthn';

export default function SecurityKeySettings({ totpStatus, onLogout }) {
    const [name, setName] = useState('');
    const [password, setPassword] = useState('');
    const [deviceId, setDeviceId] = useState('');
    const [code, setCode] = useState('');
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState('');
    const [error, setError] = useState('');
    const [done, setDone] = useState(false);
    const submitting = useRef(false);

    async function submit(event) {
        event.preventDefault();
        if (submitting.current) return;
        submitting.current = true;
        setBusy(true);
        setError('');
        setMessage('Preparing registration…');
        try {
            await registerSecurityKey({ displayName: name.trim(), password,
                existingDeviceId: deviceId ? Number(deviceId) : null, existingCode: code || null }, setMessage);
            setDone(true);
            setMessage('Security key registered. Sign in again to verify it.');
        } catch (failure) {
            setMessage('');
            setError(failure.message || 'Registration failed. Please try again.');
        } finally {
            setPassword('');
            setCode('');
            setBusy(false);
            submitting.current = false;
        }
    }

    return <section className="settings-card">
        <h2>Security keys and passkeys</h2>
        <p>Register a security key or passkey for verification after your password.</p>
        {done ? <>
            <p role="status">{message}</p>
            <button type="button" onClick={onLogout}>Sign in again</button>
        </> : <form className="settings-removal-form" onSubmit={submit}>
            <label htmlFor="key-name">Device name</label>
            <input id="key-name" required maxLength={100} value={name} disabled={busy}
                placeholder="My security key" onChange={event => setName(event.target.value)} />
            <label htmlFor="key-password">Current password</label>
            <input id="key-password" type="password" autoComplete="current-password" required
                value={password} disabled={busy} onChange={event => setPassword(event.target.value)} />
            {totpStatus?.enabled && <>
                <label htmlFor="key-authorizer">Existing verification method</label>
                <select id="key-authorizer" value={deviceId} disabled={busy} onChange={event => setDeviceId(event.target.value)}>
                    <option value="">Already registered security key or passkey</option>
                    {totpStatus.devices.map(device => <option key={device.deviceId} value={device.deviceId}>{device.displayName} (authenticator code)</option>)}
                </select>
                <p>For your first security key, select an authenticator device. If you already registered a security key, use it to confirm.</p>
                {deviceId && <>
                    <label htmlFor="key-code">Fresh authenticator code</label>
                    <input id="key-code" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}"
                        maxLength={6} required disabled={busy} value={code}
                        onChange={event => setCode(event.target.value.replace(/[^0-9]/g, '').slice(0, 6))} />
                </>}
            </>}
            <p>If you have an existing security key, the browser will ask you to verify it before registering the new one.</p>
            {message && <p role="status">{message}</p>}
            {error && <p className="message error" role="alert">{error}</p>}
            <button type="submit" disabled={busy || !name.trim() || !totpStatus}>
                {busy ? 'Waiting for verification…' : 'Register security key or passkey'}
            </button>
        </form>}
    </section>;
}
