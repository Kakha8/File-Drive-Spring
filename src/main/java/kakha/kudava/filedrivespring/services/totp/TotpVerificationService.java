package kakha.kudava.filedrivespring.services.totp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;

/** Fixed device profile: 20-byte seed, HMAC-SHA1, six ASCII digits, 30-second steps. */
@Service
public class TotpVerificationService {
    public static final int SECRET_LENGTH = 20;
    public static final int PERIOD_SECONDS = 30;
    private final Clock clock;

    @Autowired
    public TotpVerificationService() {
        this(Clock.systemUTC());
    }

    public TotpVerificationService(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Checks current step +/- one. This method does NOT consume an OTP in the database.
     * The caller must lock the device or perform an optimistic compare-and-set, persist
     * the returned counter, and consume the login challenge atomically before issuing tokens.
     * Rate limiting, device status, account ownership and enrollment expiry are caller duties.
     */
    public OptionalLong verify(byte[] secret, String code, Long lastAcceptedCounter) {
        requireSecret(secret);
        if (code == null || !code.matches("[0-9]{6}")) return OptionalLong.empty();
        long seconds = clock.instant().getEpochSecond();
        if (seconds < 0) return OptionalLong.empty();
        long current = seconds / PERIOD_SECONDS;
        byte[] received = code.getBytes(StandardCharsets.US_ASCII);
        long matched = -1;
        // Check every candidate and choose the latest matching counter.
        for (long candidate = Math.max(0, current - 1); candidate <= current + 1; candidate++) {
            byte[] expected = codeForCounter(secret, candidate).getBytes(StandardCharsets.US_ASCII);
            boolean matches = MessageDigest.isEqual(expected, received);
            Arrays.fill(expected, (byte) 0);
            if (matches && (lastAcceptedCounter == null || candidate > lastAcceptedCounter)) {
                matched = candidate;
            }
        }
        Arrays.fill(received, (byte) 0);
        return matched < 0 ? OptionalLong.empty() : OptionalLong.of(matched);
    }

    /** Exact ESP32 export format: 32 Base32 characters, case-insensitive, no padding. */
    public byte[] decodeBase32Secret(String encoded) {
        if (encoded == null || !encoded.matches("[A-Za-z2-7]{32}")) {
            throw new IllegalArgumentException("TOTP seed must contain exactly 32 Base32 characters.");
        }
        String normalized = encoded.toUpperCase(Locale.ROOT);
        byte[] result = new byte[SECRET_LENGTH];
        int buffer = 0, bits = 0, index = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            int value = c <= 'Z' && c >= 'A' ? c - 'A' : c - '2' + 26;
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                result[index++] = (byte) (buffer >> bits);
            }
        }
        return result;
    }

    static void requireSecret(byte[] secret) {
        if (secret == null || secret.length != SECRET_LENGTH) {
            throw new IllegalArgumentException("TOTP seed must contain exactly 20 bytes.");
        }
    }

    private String codeForCounter(byte[] secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            try {
                int offset = hash[hash.length - 1] & 0x0f;
                int binary = ((hash[offset] & 0x7f) << 24)
                        | ((hash[offset + 1] & 0xff) << 16)
                        | ((hash[offset + 2] & 0xff) << 8)
                        | (hash[offset + 3] & 0xff);
                return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
            } finally {
                Arrays.fill(hash, (byte) 0);
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP verification is unavailable.", exception);
        }
    }
}
