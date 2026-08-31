package kakha.kudava.filedrivespring.services.totp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/** Encrypts device seeds; never stores or logs plaintext secrets. */
@Service
public class TotpSecretEncryptionService {
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private final SecureRandom random = new SecureRandom();
    private final String keyId;
    private final SecretKeySpec key;

    public TotpSecretEncryptionService(
            @Value("${app.totp.encryption-key-id:}") String keyId,
            @Value("${app.totp.encryption-key-base64:}") String keyBase64) {
        this.keyId = keyId == null ? "" : keyId.trim();
        if (this.keyId.isEmpty() && (keyBase64 == null || keyBase64.isBlank())) {
            // TOTP is optional. No encryption operation is allowed without a key.
            this.key = null;
            return;
        }
        if (!this.keyId.matches("[A-Za-z0-9._-]{1,100}")) {
            throw new IllegalArgumentException("TOTP encryption key ID is missing or invalid.");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(keyBase64 == null ? "" : keyBase64);
        } catch (IllegalArgumentException exception) {
            // Do not include input or parser exception details in this error.
            throw new IllegalArgumentException("TOTP encryption key must be Base64-encoded 32 bytes.");
        }
        try {
            if (decoded.length != 32) {
                throw new IllegalArgumentException("TOTP encryption key must be Base64-encoded 32 bytes.");
            }
            this.key = new SecretKeySpec(decoded, "AES");
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
    }

    /** Bind the ciphertext to the immutable account UUID, not a request-supplied owner. */
    public EncryptedSecret encrypt(UUID userPublicUuid, byte[] secret) {
        requireConfigured();
        TotpVerificationService.requireSecret(secret);
        byte[] nonce = new byte[NONCE_LENGTH];
        random.nextBytes(nonce);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, userPublicUuid, nonce);
            return new EncryptedSecret(cipher.doFinal(secret), nonce, keyId);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt TOTP secret.", exception);
        }
    }

    /** Caller owns the returned plaintext and should clear it in a finally block. */
    public byte[] decrypt(UUID userPublicUuid, byte[] ciphertext, byte[] nonce, String storedKeyId) {
        requireConfigured();
        if (!keyId.equals(storedKeyId)) {
            throw new IllegalStateException("TOTP encryption key ID is unavailable.");
        }
        if (nonce == null || nonce.length != NONCE_LENGTH
                || ciphertext == null || ciphertext.length != TotpVerificationService.SECRET_LENGTH + TAG_BITS / 8) {
            throw new IllegalArgumentException("Invalid encrypted TOTP secret.");
        }
        try {
            return cipher(Cipher.DECRYPT_MODE, userPublicUuid, nonce).doFinal(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to authenticate or decrypt TOTP secret.", exception);
        }
    }

    private Cipher cipher(int mode, UUID userPublicUuid, byte[] nonce) throws GeneralSecurityException {
        Objects.requireNonNull(userPublicUuid, "Account public UUID is required.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(("FD-TOTP-SEED-V1\0" + userPublicUuid + "\0" + keyId)
                .getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private void requireConfigured() {
        if (key == null) {
            throw new IllegalStateException("TOTP encryption is not configured.");
        }
    }

    /** Fields map directly to TotpDevice; defensive copies prevent accidental mutation. */
    public record EncryptedSecret(byte[] ciphertext, byte[] nonce, String keyId) {
        public EncryptedSecret {
            ciphertext = ciphertext.clone();
            nonce = nonce.clone();
        }

        @Override
        public byte[] ciphertext() { return ciphertext.clone(); }

        @Override
        public byte[] nonce() { return nonce.clone(); }

        @Override
        public String toString() { return "EncryptedSecret[redacted]"; }
    }
}
