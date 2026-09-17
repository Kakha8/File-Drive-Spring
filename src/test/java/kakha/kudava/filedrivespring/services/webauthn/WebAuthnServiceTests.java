package kakha.kudava.filedrivespring.services.webauthn;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.upokecenter.cbor.CBORObject;
import kakha.kudava.filedrivespring.model.*;
import kakha.kudava.filedrivespring.repository.*;
import kakha.kudava.filedrivespring.services.jwt.*;
import kakha.kudava.filedrivespring.services.totp.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "JWT_REFRESH_DAYS=7",
        "app.webauthn.enabled=true", "app.webauthn.rp-id=localhost", "app.webauthn.origins=http://localhost:5173"}, showSql = false)
@Import({WebAuthnService.class, WebAuthnEngine.class, WebAuthnCredentials.class,
        TwoStageLoginService.class, JwtRefreshService.class, WebAuthnServiceTests.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WebAuthnServiceTests {
    static final ObjectMapper JSON = new ObjectMapper();
    @Autowired WebAuthnService service;
    @Autowired TwoStageLoginService login;
    @Autowired JwtRefreshService refresh;
    @Autowired UserRepository users;
    @Autowired WebAuthnCredentialRepository credentials;
    @Autowired WebAuthnCeremonyRepository ceremonies;
    @Autowired MfaLoginChallengeRepository challenges;
    @Autowired JwtRefresherRepository tokens;
    @Autowired LoginAttemptLimitRepository limits;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    User owner;
    KeyPair key;
    byte[] id;

    @BeforeEach void setup() throws Exception {
        tokens.deleteAll(); ceremonies.deleteAll(); challenges.deleteAll(); credentials.deleteAll(); limits.deleteAll(); users.deleteAll();
        owner = user("alice");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1")); key = generator.generateKeyPair();
        id = new byte[32]; new SecureRandom().nextBytes(id);
    }
    User user(String name) {
        User u = new User(); u.setUsername(name); u.setPassword(passwords.encode("password")); u.setRole(User.Role.USER);
        return users.saveAndFlush(u);
    }
    static String b64(byte[] b) { return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    static byte[] sha(byte[] b) throws Exception { return MessageDigest.getInstance("SHA-256").digest(b); }
    static byte[] coordinate(BigInteger i) {
        byte[] b = i.toByteArray(); return Arrays.copyOfRange(b.length > 32 ? b : pad(b), Math.max(0, b.length - 32), b.length > 32 ? b.length : 32);
    }
    static byte[] pad(byte[] b) { byte[] p = new byte[32]; System.arraycopy(b, 0, p, 32 - b.length, b.length); return p; }
    byte[] cose() {
        ECPublicKey p = (ECPublicKey) key.getPublic();
        CBORObject c = CBORObject.NewMap(); c.Add(1, 2); c.Add(3, -7); c.Add(-1, 1);
        c.Add(-2, coordinate(p.getW().getAffineX())); c.Add(-3, coordinate(p.getW().getAffineY()));
        return c.EncodeToBytes();
    }
    byte[] client(String type, JsonNode options, String origin) throws Exception {
        return JSON.writeValueAsBytes(JSON.createObjectNode().put("type", type)
                .put("challenge", options.path("challenge").asText()).put("origin", origin).put("crossOrigin", false));
    }
    ObjectNode responseBase() {
        var r = JSON.createObjectNode().put("id", b64(id)).put("rawId", b64(id)).put("type", "public-key");
        r.set("clientExtensionResults", JSON.createObjectNode()); return r;
    }
    ObjectNode registration(WebAuthnService.Options o, String origin) throws Exception {
        ByteArrayOutputStream a = new ByteArrayOutputStream(); a.write(sha("localhost".getBytes(StandardCharsets.UTF_8)));
        a.write(0x41); a.write(new byte[4]); a.write(new byte[16]); a.write(ByteBuffer.allocate(2).putShort((short) id.length).array());
        a.write(id); a.write(cose());
        CBORObject att = CBORObject.NewMap(); att.Add("fmt", "none"); att.Add("attStmt", CBORObject.NewMap()); att.Add("authData", a.toByteArray());
        ObjectNode r = responseBase(); r.set("response", JSON.createObjectNode()
                .put("clientDataJSON", b64(client("webauthn.create", o.publicKey(), origin)))
                .put("attestationObject", b64(att.EncodeToBytes()))); return r;
    }
    ObjectNode assertion(JsonNode options, String origin, String rp, int flags, boolean badSignature) throws Exception {
        byte[] client = client("webauthn.get", options, origin);
        ByteArrayOutputStream a = new ByteArrayOutputStream(); a.write(sha(rp.getBytes(StandardCharsets.UTF_8))); a.write(flags); a.write(new byte[4]);
        byte[] authData = a.toByteArray();
        Signature signer = Signature.getInstance("SHA256withECDSA"); signer.initSign(key.getPrivate()); signer.update(authData); signer.update(sha(client));
        byte[] sig = signer.sign(); if (badSignature) sig[sig.length - 1] ^= 1;
        ObjectNode r = responseBase(); r.set("response", JSON.createObjectNode().put("clientDataJSON", b64(client))
                .put("authenticatorData", b64(authData)).put("signature", b64(sig))
                .put("userHandle", WebAuthnCredentials.handle(owner).getBase64Url())); return r;
    }
    void enroll() throws Exception {
        var o = service.beginRegistration("alice", "password", "ESP32", null, null);
        service.finishRegistration("alice", o.requestId(), registration(o, "http://localhost:5173"), null);
    }
    @Test void credentialStatusListsOnlyTheAuthenticatedUsersCredentials() throws Exception {
        enroll();
        var status = service.credentialStatus("alice");
        assertTrue(status.enabled());
        assertEquals(1, status.devices().size());
        assertEquals("ESP32", status.devices().getFirst().displayName());
        assertNotNull(status.devices().getFirst().createdAt());
        assertNull(status.devices().getFirst().lastUsedAt());
        assertThrows(WebAuthnService.Rejected.class, () -> service.credentialStatus("missing"));
    }
    @Test void registeredKeyCanAuthorizeItsRemoval() throws Exception {
        enroll();
        Long credentialId = credentials.findAll().getFirst().getId();
        var options = service.beginRemoval("alice", credentialId, "password", null, null);
        assertNotNull(options.authorizationPublicKey());
        var allowCredentials = options.authorizationPublicKey().path("allowCredentials");
        assertEquals("RU5JR01BX1JFTU9WQUxfVjE", allowCredentials.path(0).path("id").asText());
        assertEquals("public-key", allowCredentials.path(0).path("type").asText());
        var removed = service.finishRemoval("alice", credentialId, options.requestId(),
                assertion(options.authorizationPublicKey(), "http://localhost:5173", "localhost", 1, false));
        assertEquals(credentialId, removed.removedCredentialRecordId());
        assertEquals(0, removed.remainingDevices());
        assertFalse(removed.enabled());
        assertEquals(0, credentials.count());
        assertFalse(users.findById(owner.getId()).orElseThrow().isWebauthnEnabled());
    }

    @Test void removalRejectsAnotherUsersCredentialAndWrongPassword() throws Exception {
        enroll();
        Long credentialId = credentials.findAll().getFirst().getId();
        user("bob");
        assertThrows(WebAuthnService.Rejected.class,
                () -> service.beginRemoval("bob", credentialId, "password", null, null));
        assertThrows(WebAuthnService.Rejected.class,
                () -> service.beginRemoval("alice", credentialId, "wrong", null, null));
        assertEquals(1, credentials.count());
    }
    @Test void realRegistrationAndSignatureProduceSessionAndConsumeChallenge() throws Exception {
        enroll(); assertTrue(users.findById(owner.getId()).orElseThrow().isWebauthnEnabled());
        var passwordStep = login.login("alice", "password"); assertNull(passwordStep.session());
        assertEquals("webauthn", passwordStep.challenge().method());
        String token = passwordStep.challenge().challengeToken(); var o = service.beginLogin(token);
        var response = assertion(o.publicKey(), "http://localhost:5173", "localhost", 1, false);
        var session = service.finishLogin(token, o.requestId(), response);
        assertNotNull(session.login().getAccessToken());
        assertNotNull(credentials.findAll().getFirst().getLastUsedAt());
        assertThrows(WebAuthnService.Rejected.class, () -> service.finishLogin(token, o.requestId(), response));
    }
    @Test void wrongOriginRegistrationDoesNotActivateAndCannotReplay() throws Exception {
        var o = service.beginRegistration("alice", "password", "ESP32", null, null);
        assertThrows(WebAuthnService.Rejected.class, () -> service.finishRegistration("alice", o.requestId(), registration(o, "https://evil.example"), null));
        assertFalse(users.findById(owner.getId()).orElseThrow().isWebauthnEnabled()); assertEquals(0, credentials.count());
        assertThrows(WebAuthnService.Rejected.class, () -> service.finishRegistration("alice", o.requestId(), registration(o, "http://localhost:5173"), null));
    }
    @Test void rejectsWrongOriginRpSignatureChallengeAndMissingPresence() throws Exception {
        enroll();
        for (int variant = 0; variant < 5; variant++) {
            String token = login.login("alice", "password").challenge().challengeToken(); var o = service.beginLogin(token);
            JsonNode options = o.publicKey().deepCopy();
            if (variant == 3) ((ObjectNode) options).put("challenge", b64(new byte[32]));
            var response = assertion(options, variant == 0 ? "https://evil.example" : "http://localhost:5173",
                    variant == 1 ? "evil.example" : "localhost", variant == 4 ? 0 : 1, variant == 2);
            assertThrows(WebAuthnService.Rejected.class, () -> service.finishLogin(token, o.requestId(), response));
        }
        assertEquals(0, tokens.count());
    }
    @Test void rejectsCrossAccountExpiredAndPasswordChangedRegistration() throws Exception {
        user("bob"); var o = service.beginRegistration("alice", "password", "ESP32", null, null);
        assertThrows(WebAuthnService.Rejected.class, () -> service.finishRegistration("bob", o.requestId(), registration(o, "http://localhost:5173"), null));
        new TransactionTemplate(transactions).executeWithoutResult(s -> {
            var c = ceremonies.findById(o.requestId()).orElseThrow(); c.setExpiresAt(Instant.now().minusSeconds(1));
        });
        assertThrows(WebAuthnService.Rejected.class, () -> service.finishRegistration("alice", o.requestId(), registration(o, "http://localhost:5173"), null));
        var next = service.beginRegistration("alice", "password", "ESP32", null, null);
        owner.setPassword(passwords.encode("changed")); users.saveAndFlush(owner);
        assertThrows(WebAuthnService.Rejected.class, () -> service.finishRegistration("alice", next.requestId(), registration(next, "http://localhost:5173"), null));
    }
    @Test void enrollmentRequiresPasswordAndExistingFactor() throws Exception {
        assertThrows(WebAuthnService.Rejected.class, () -> service.beginRegistration("alice", "wrong", "ESP32", null, null));
        enroll(); var o = service.beginRegistration("alice", "password", "second", null, null);
        assertNotNull(o.authorizationPublicKey());
        assertThrows(WebAuthnService.Rejected.class, () -> service.finishRegistration("alice", o.requestId(), registration(o, "http://localhost:5173"), null));
        assertEquals(1, credentials.count());
    }
    @Test void additionalCredentialRequiresValidExistingSignature() throws Exception {
        enroll(); var o = service.beginRegistration("alice", "password", "second", null, null);
        var authorization = assertion(o.authorizationPublicKey(), "http://localhost:5173", "localhost", 1, false);
        id = new byte[32]; new SecureRandom().nextBytes(id);
        service.finishRegistration("alice", o.requestId(), registration(o, "http://localhost:5173"), authorization);
        assertEquals(2, credentials.count());
    }
    @Test void expiredLoginAndTotpCannotBypassWebauthn() throws Exception {
        enroll(); String token = login.login("alice", "password").challenge().challengeToken(); var o = service.beginLogin(token);
        new TransactionTemplate(transactions).executeWithoutResult(s -> ceremonies.findById(o.requestId()).orElseThrow().setExpiresAt(Instant.now().minusSeconds(1)));
        assertThrows(WebAuthnService.Rejected.class, () -> service.finishLogin(token, o.requestId(), assertion(o.publicKey(), "http://localhost:5173", "localhost", 1, false)));
        String next = login.login("alice", "password").challenge().challengeToken();
        assertThrows(TwoStageLoginService.LoginRejected.class, () -> login.verify(next, "123456"));
        assertThrows(IllegalStateException.class, () -> refresh.createToken(users.findById(owner.getId()).orElseThrow(), 7));
        assertEquals(0, tokens.count());
    }
    @Test void replacingOptionsInvalidatesPreviousCeremony() throws Exception {
        var first = service.beginRegistration("alice", "password", "ESP32", null, null);
        service.beginRegistration("alice", "password", "ESP32", null, null);
        assertThrows(WebAuthnService.Rejected.class, () -> service.finishRegistration("alice", first.requestId(), registration(first, "http://localhost:5173"), null));
    }
    @Test void simultaneousFinishIssuesOnlyOneSession() throws Exception {
        enroll(); String token = login.login("alice", "password").challenge().challengeToken(); var o = service.beginLogin(token);
        var response = assertion(o.publicKey(), "http://localhost:5173", "localhost", 1, false);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> finish = () -> {
                start.await();
                try { service.finishLogin(token, o.requestId(), response); return true; }
                catch (WebAuthnService.Rejected e) { return false; }
            };
            var first = executor.submit(finish); var second = executor.submit(finish); start.countDown();
            assertNotEquals(first.get(10, java.util.concurrent.TimeUnit.SECONDS), second.get(10, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1, tokens.count());
    }
    @Test void activationRevokesExistingRefreshSession() throws Exception {
        var previous = login.login("alice", "password").session();
        enroll();
        assertThrows(JwtRefreshService.RefreshRejected.class, () -> refresh.rotate(previous.refreshToken(), 7));
    }
    @TestConfiguration static class Config {
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(4); }
        @Bean JwtService jwtService() { return new JwtService("test-only-signing-key-at-least-32-bytes-long", 15); }
        @Bean TotpSecretEncryptionService encryption() { return mock(TotpSecretEncryptionService.class); }
        @Bean TotpVerificationService verifier() { return new TotpVerificationService(); }
    }
}
