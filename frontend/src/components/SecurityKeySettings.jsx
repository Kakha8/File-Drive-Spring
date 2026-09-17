import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { registerSecurityKey } from '../api/webauthn';
import SecurityKeyIcon from './SecurityKeyIcon';
import './SecurityKeySettings.css';

export default function SecurityKeySettings({ totpStatus, onLogout }) {
    const [name, setName] = useState('');
    const [password, setPassword] = useState('');
    const [deviceId, setDeviceId] = useState('');
    const [code, setCode] = useState('');
    const [busy, setBusy] = useState(false);
    const [open, setOpen] = useState(false);
    const [message, setMessage] = useState('');
    const [error, setError] = useState('');
    const submitting = useRef(false);
    const dialog = useRef(null);
    const trigger = useRef(null);

    useEffect(() => {
        if (!open) return undefined;
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        const focusable = dialog.current?.querySelector('input');
        focusable?.focus();
        function onKeyDown(event) {
            if (event.key === 'Escape' && !submitting.current) {
                event.preventDefault();
                close();
            }
            if (event.key !== 'Tab') return;
            const controls = [...(dialog.current?.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled)') || [])];
            if (!controls.length) return;
            const first = controls[0], last = controls[controls.length - 1];
            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
        }
        document.addEventListener('keydown', onKeyDown);
        return () => { document.body.style.overflow = previousOverflow; document.removeEventListener('keydown', onKeyDown); };
    }, [open]);

    function close() {
        if (submitting.current) return;
        setOpen(false);
        setName(''); setPassword(''); setDeviceId(''); setCode(''); setMessage(''); setError('');
        requestAnimationFrame(() => trigger.current?.focus());
    }

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
            setMessage('Security key registered. Signing out…');
            onLogout?.();
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

    return <section className="settings-card security-key-settings">
        <div className="security-key-settings-summary">
            <div>
                <h2>Security keys and passkeys</h2>
                <p>Add a security key or passkey to verify sign-ins after your password.</p>
            </div>
            <button ref={trigger} type="button" className="security-key-add-button" onClick={() => setOpen(true)}>
                <span aria-hidden="true">＋</span> Add security key
            </button>
        </div>
        {open && createPortal(<div className="security-key-modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) close(); }}>
            <section ref={dialog} className="security-key-modal" role="dialog" aria-modal="true" aria-labelledby="security-key-modal-title" aria-describedby="security-key-modal-description">
                <div className="security-key-modal-header">
                    <span className="security-key-modal-icon" aria-hidden="true"><SecurityKeyIcon /></span>
                    <div>
                        <h2 id="security-key-modal-title">Add a security key</h2>
                        <p id="security-key-modal-description">Use your account password to register a new key or passkey.</p>
                    </div>
                    <button type="button" className="security-key-modal-close" aria-label="Close" disabled={busy} onClick={close}>×</button>
                </div>
                <form className="security-key-modal-form" onSubmit={submit}>
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
            <p className="security-key-modal-note">If you already have a security key, your browser may ask you to verify it first.</p>
            {message && <p className="security-key-modal-status" role="status">{message}</p>}
            {error && <p className="security-key-modal-error" role="alert">{error}</p>}
            <div className="security-key-modal-actions">
                <button type="button" className="security-key-modal-cancel" disabled={busy} onClick={close}>Cancel</button>
                <button type="submit" className="security-key-modal-submit" disabled={busy || !name.trim() || !totpStatus}>
                    {busy ? 'Waiting for verification…' : 'Register key'}
                </button>
            </div>
                </form>
            </section>
        </div>, document.body)}
    </section>;
}
