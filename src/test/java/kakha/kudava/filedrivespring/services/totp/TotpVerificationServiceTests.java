package kakha.kudava.filedrivespring.services.totp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class TotpVerificationServiceTests {
    private static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    // RFC 6238 Appendix B SHA1 vectors reduced from eight digits to six.
    @ParameterizedTest
    @CsvSource({"59,287082", "1111111109,081804", "1111111111,050471",
            "1234567890,005924", "2000000000,279037", "20000000000,353130"})
    void matchesRfcVectors(long seconds, String code) {
        assertEquals(seconds / 30, at(seconds).verify(SECRET, code, null).orElseThrow());
    }

    @Test
    void acceptsOnlyAdjacentStepsAndNeverNegativeTime() {
        for (long second : new long[]{0, 29, 30, 59, 60, 89}) {
            assertEquals(1, at(second).verify(SECRET, "287082", null).orElseThrow());
        }
        assertTrue(at(90).verify(SECRET, "287082", null).isEmpty());
        assertTrue(at(-1).verify(SECRET, "287082", null).isEmpty());
    }

    @Test
    void rejectsConsumedAndOlderCounters() {
        assertEquals(1, at(59).verify(SECRET, "287082", 0L).orElseThrow());
        assertTrue(at(59).verify(SECRET, "287082", 1L).isEmpty());
        assertTrue(at(59).verify(SECRET, "287082", 2L).isEmpty());
    }

    @Test
    void rejectsMalformedAndWrongCodes() {
        for (String code : new String[]{null, "", "28708", "0287082", "287082 ", " 287082",
                "abcdef", "２８７０８２", "000000"}) {
            assertTrue(at(59).verify(SECRET, code, null).isEmpty());
        }
    }

    @Test
    void decodesExactDeviceProfileWithoutLeakingBadInput() {
        TotpVerificationService service = at(59);
        String encoded = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertArrayEquals(SECRET, service.decodeBase32Secret(encoded));
        assertArrayEquals(SECRET, service.decodeBase32Secret(encoded.toLowerCase(Locale.ROOT)));
        for (String bad : new String[]{null, "", encoded + "=", " " + encoded,
                encoded.substring(1), "0".repeat(32), "A".repeat(33)}) {
            assertThrows(IllegalArgumentException.class, () -> service.decodeBase32Secret(bad));
        }
        for (byte[] bad : new byte[][]{null, new byte[0], new byte[19], new byte[21]}) {
            assertThrows(IllegalArgumentException.class, () -> service.verify(bad, "287082", null));
        }
    }

    private TotpVerificationService at(long second) {
        return new TotpVerificationService(Clock.fixed(Instant.ofEpochSecond(second), ZoneOffset.UTC));
    }
}
