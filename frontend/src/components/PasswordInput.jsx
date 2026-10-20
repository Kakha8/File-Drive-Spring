import { useState } from "react";

function EyeIcon({ hidden }) {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" />
            <circle cx="12" cy="12" r="2.75" />
            {hidden && <path d="M4 4l16 16" />}
        </svg>
    );
}

export default function PasswordInput({ className = "", ...inputProps }) {
    const [visible, setVisible] = useState(false);

    return (
        <span className={`password-input ${className}`.trim()}>
            <input {...inputProps} type={visible ? "text" : "password"} />
            <button
                type="button"
                className="password-input-toggle"
                onClick={() => setVisible((value) => !value)}
                aria-label={visible ? "Hide password" : "Show password"}
                aria-pressed={visible}
                disabled={inputProps.disabled}
            >
                <EyeIcon hidden={visible} />
            </button>
        </span>
    );
}
