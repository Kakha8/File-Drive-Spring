package kakha.kudava.filedrivespring.services.totp;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TotpSecretEncryptionServiceTests {
    private final UUID owner = UUID.randomUUID();
    private final byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private final TotpSecretEncryptionService service = service((byte) 1);

    @Test
    void roundTripHasFreshNonceAndDoesNotMutateInput() {
        var first = service.encrypt(owner, secret);
        var second = service.encrypt(owner, secret);
        assertEquals(12, first.nonce().length);
        assertEquals(36, first.ciphertext().length);
        assertEquals("test-v1", first.keyId());
        assertFalse(Arrays.equals(first.nonce(), second.nonce()));
        assertFalse(Arrays.equals(first.ciphertext(), second.ciphertext()));
        assertArrayEquals(secret, service.decrypt(owner, first.ciphertext(), first.nonce(), first.keyId()));
        assertArrayEquals("12345678901234567890".getBytes(StandardCharsets.US_ASCII), secret);
    }

    @Test
    void rejectsTamperingWrongOwnerWrongKeyAndUnknownKeyId() {
        var encrypted = service.encrypt(owner, secret);
        byte[] ciphertext = encrypted.ciphertext();
        ciphertext[0] ^= 1;
        assertThrows(IllegalStateException.class,
                () -> service.decrypt(owner, ciphertext, encrypted.nonce(), encrypted.keyId()));
        byte[] nonce = encrypted.nonce();
        nonce[0] ^= 1;
        assertThrows(IllegalStateException.class,
                () -> service.decrypt(owner, encrypted.ciphertext(), nonce, encrypted.keyId()));
        assertThrows(IllegalStateException.class,
                () -> service.decrypt(UUID.randomUUID(), encrypted.ciphertext(), encrypted.nonce(), encrypted.keyId()));
        assertThrows(IllegalStateException.class,
                () -> service((byte) 2).decrypt(owner, encrypted.ciphertext(), encrypted.nonce(), encrypted.keyId()));
        assertThrows(IllegalStateException.class,
                () -> service.decrypt(owner, encrypted.ciphertext(), encrypted.nonce(), "unknown"));
    }

    @Test
    void validatesConfigurationAndFailsClosedWhenMissing() {
        var unconfigured = new TotpSecretEncryptionService("", "");
        assertThrows(IllegalStateException.class, () -> unconfigured.encrypt(owner, secret));
        assertThrows(IllegalStateException.class, () -> unconfigured.decrypt(owner, new byte[36], new byte[12], ""));
        for (String key : new String[]{null, "", "not-base64!", b64(new byte[16]), b64(new byte[31]), b64(new byte[33])}) {
            assertThrows(IllegalArgumentException.class, () -> new TotpSecretEncryptionService("v1", key));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new TotpSecretEncryptionService("", b64(new byte[32])));
    }

    @Test
    void validatesEncryptedPayloadAndPlaintextLengths() {
        assertThrows(IllegalArgumentException.class, () -> service.encrypt(owner, new byte[19]));
        assertThrows(NullPointerException.class, () -> service.encrypt(null, secret));
        assertThrows(IllegalArgumentException.class,
                () -> service.decrypt(owner, new byte[36], new byte[11], "test-v1"));
        assertThrows(IllegalArgumentException.class,
                () -> service.decrypt(owner, new byte[35], new byte[12], "test-v1"));
    }

    @Test
    void returnedPayloadDefensivelyCopiesArraysAndRedactsToString() {
        byte[] ciphertext = new byte[36], nonce = new byte[12];
        var payload = new TotpSecretEncryptionService.EncryptedSecret(ciphertext, nonce, "id");
        ciphertext[0] = 1; nonce[0] = 1;
        assertEquals(0, payload.ciphertext()[0]);
        assertEquals(0, payload.nonce()[0]);
        payload.ciphertext()[0] = 2; payload.nonce()[0] = 2;
        assertEquals(0, payload.ciphertext()[0]);
        assertEquals(0, payload.nonce()[0]);
        assertEquals("EncryptedSecret[redacted]", payload.toString());
    }

    private static TotpSecretEncryptionService service(byte value) {
        byte[] key = new byte[32];
        Arrays.fill(key, value);
        return new TotpSecretEncryptionService("test-v1", b64(key));
    }

    private static String b64(byte[] bytes) { return Base64.getEncoder().encodeToString(bytes); }
}
