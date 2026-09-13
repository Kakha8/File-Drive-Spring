package kakha.kudava.filedrivespring.services.webauthn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import kakha.kudava.filedrivespring.model.*;
import kakha.kudava.filedrivespring.repository.*;
import kakha.kudava.filedrivespring.security.TokenHashUtil;
import kakha.kudava.filedrivespring.services.jwt.*;
import kakha.kudava.filedrivespring.services.totp.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
@Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = WebAuthnService.Rejected.class)
public class WebAuthnService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final WebAuthnEngine engine;
    private final UserRepository users;
    private final WebAuthnCredentialRepository credentials;
    private final WebAuthnCeremonyRepository ceremonies;
    private final MfaLoginChallengeRepository challenges;
    private final LoginAttemptLimitRepository limits;
    private final PasswordEncoder passwords;
    private final TotpDeviceRepository totpDevices;
    private final TotpSecretEncryptionService encryption;
    private final TotpVerificationService totpVerifier;
    private final JwtRefreshService refresh;
    private final int refreshDays;

    public WebAuthnService(WebAuthnEngine engine, UserRepository users, WebAuthnCredentialRepository credentials,
            WebAuthnCeremonyRepository ceremonies, MfaLoginChallengeRepository challenges,
            LoginAttemptLimitRepository limits, PasswordEncoder passwords, TotpDeviceRepository totpDevices,
            TotpSecretEncryptionService encryption, TotpVerificationService totpVerifier, JwtRefreshService refresh,
            @Value("${JWT_REFRESH_DAYS}") int refreshDays) {
        this.engine = engine; this.users = users; this.credentials = credentials; this.ceremonies = ceremonies;
        this.challenges = challenges; this.limits = limits; this.passwords = passwords; this.totpDevices = totpDevices;
        this.encryption = encryption; this.totpVerifier = totpVerifier; this.refresh = refresh; this.refreshDays = refreshDays;
    }

    public record Options(UUID requestId, Instant expiresAt, JsonNode publicKey, JsonNode authorizationPublicKey) {}
    public record Registered(Long credentialRecordId, String displayName) {}
    public static class Rejected extends ResponseStatusException {
        public Rejected() { super(HttpStatus.UNAUTHORIZED, "Invalid or expired WebAuthn request."); }
    }

    private User locked(String name) {
        if (name == null || name.isBlank()) throw new Rejected();
        return users.findForTotpEnrollment(name).orElseThrow(Rejected::new);
    }
    private LoginAttemptLimit limit(User user) {
        Instant now = Instant.now();
        LoginAttemptLimit value = limits.findById(user.getId()).orElseGet(() -> {
            var created = new LoginAttemptLimit(); created.setUser(user); created.setWindowStartedAt(now);
            return limits.save(created);
        });
        if (!now.isBefore(value.getWindowStartedAt().plusSeconds(900))) {
            value.setWindowStartedAt(now); value.setFailures(0); value.setChallenges(0);
        }
        if (value.getFailures() >= 10 || value.getChallenges() >= 20)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many authentication attempts.");
        return value;
    }
    private void fail(LoginAttemptLimit limit) { limit.setFailures(limit.getFailures() + 1); throw new Rejected(); }
    private WebAuthnCeremony start(User user, WebAuthnCeremony.Kind kind) {
        var limit = limit(user); limit.setChallenges(limit.getChallenges() + 1);
        ceremonies.deleteAllByUserIdAndKind(user.getId(), kind);
        var c = new WebAuthnCeremony(); c.setId(UUID.randomUUID()); c.setUser(user); c.setKind(kind);
        c.setExpiresAt(Instant.now().plusSeconds(180));
        c.setPasswordFingerprint(TokenHashUtil.sha256(user.getPassword()));
        return c;
    }
    private JsonNode publicKey(String json) {
        try { return JSON.readTree(json).get("publicKey"); }
        catch (Exception e) { throw new IllegalStateException("Could not encode WebAuthn options."); }
    }
    private AssertionRequest assertion(User user) {
        return engine.relyingParty().startAssertion(StartAssertionOptions.builder()
                .userHandle(WebAuthnCredentials.handle(user)).userVerification(UserVerificationRequirement.PREFERRED)
                .timeout(180000).build());
    }

    public Options beginRegistration(String username, String password, String name, Long totpDeviceId, String code) {
        var rp = engine.relyingParty();
        User user = locked(username);
        var limit = limit(user);
        if (password == null || password.length() > 1024 || !passwords.matches(password, user.getPassword())) fail(limit);
        if (name == null || name.isBlank() || name.length() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device name must contain 1 to 100 characters.");
        var c = start(user, WebAuthnCeremony.Kind.REGISTRATION);
        c.setDisplayName(name.strip());
        if (user.isWebauthnEnabled()) {
            if (credentials.findAllByUserId(user.getId()).isEmpty()) fail(limit);
            try { c.setAuthorizationJson(assertion(user).toJson()); }
            catch (Exception e) { throw new IllegalStateException("Could not create authorization request."); }
        } else if (user.isTotpEnabled()) {
            if (totpDeviceId == null) fail(limit);
            var device = totpDevices.findForEnrollmentUpdate(totpDeviceId, user.getId()).orElse(null);
            if (device == null || device.getStatus() != TotpDevice.Status.ACTIVE) fail(limit);
            byte[] secret = encryption.decrypt(user.getPublicUuid(), device.getEncryptedSecret(), device.getEncryptionNonce(), device.getEncryptionKeyId());
            try {
                var counter = totpVerifier.verify(secret, code, device.getLastAcceptedCounter());
                if (counter.isEmpty()) fail(limit);
                device.setLastAcceptedCounter(counter.orElseThrow());
                c.setTotpAuthorized(true);
            } finally { Arrays.fill(secret, (byte) 0); }
        }
        var request = rp.startRegistration(StartRegistrationOptions.builder()
                .user(UserIdentity.builder().name(user.getUsername()).displayName(user.getUsername()).id(WebAuthnCredentials.handle(user)).build())
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .userVerification(UserVerificationRequirement.PREFERRED).build()).timeout(180000).build());
        try {
            c.setRequestJson(request.toJson()); ceremonies.saveAndFlush(c);
            return new Options(c.getId(), c.getExpiresAt(), publicKey(request.toCredentialsCreateJson()),
                    c.getAuthorizationJson() == null ? null : publicKey(AssertionRequest.fromJson(c.getAuthorizationJson()).toCredentialsGetJson()));
        } catch (java.io.IOException e) { throw new IllegalStateException("Could not serialize WebAuthn request."); }
    }

    // Account lock serializes finishing, replacement, activation and token issuance.
    private WebAuthnCeremony consume(User user, UUID id, WebAuthnCeremony.Kind kind, LoginAttemptLimit limit) {
        if (id == null) fail(limit);
        var c = ceremonies.findById(id).orElse(null);
        if (c == null || !c.getUser().getId().equals(user.getId()) || c.getKind() != kind || c.getConsumedAt() != null) fail(limit);
        c.setConsumedAt(Instant.now()); // A failed verification consumes this attempt too.
        if (!Instant.now().isBefore(c.getExpiresAt()) || !TokenHashUtil.sha256(user.getPassword()).equals(c.getPasswordFingerprint())) fail(limit);
        return c;
    }
    private void responseSize(JsonNode response) {
        if (response == null || !response.isObject() || response.toString().length() > 65536) throw new Rejected();
    }
    private void verifyAssertion(User user, String requestJson, JsonNode response) throws Exception {
        responseSize(response);
        var result = engine.relyingParty().finishAssertion(FinishAssertionOptions.builder()
                .request(AssertionRequest.fromJson(requestJson))
                .response(PublicKeyCredential.parseAssertionResponseJson(response.toString())).build());
        if (!result.isSuccess() || !result.getUserHandle().equals(WebAuthnCredentials.handle(user))) throw new Rejected();
        var credential = credentials.findByCredentialId(result.getCredentialId().getBase64Url()).orElseThrow(Rejected::new);
        if (!credential.getUser().getId().equals(user.getId())) throw new Rejected();
        credential.setLastUsedAt(Instant.now());
    }

    public Registered finishRegistration(String username, UUID requestId, JsonNode response, JsonNode authorization) {
        engine.relyingParty();
        User user = locked(username); var limit = limit(user);
        var c = consume(user, requestId, WebAuthnCeremony.Kind.REGISTRATION, limit);
        RegistrationResult result;
        try {
            if (user.isWebauthnEnabled()) {
                if (c.getAuthorizationJson() == null) throw new Rejected();
                verifyAssertion(user, c.getAuthorizationJson(), authorization);
            } else if (user.isTotpEnabled() && !c.isTotpAuthorized()) throw new Rejected();
            responseSize(response);
            result = engine.relyingParty().finishRegistration(FinishRegistrationOptions.builder()
                    .request(PublicKeyCredentialCreationOptions.fromJson(c.getRequestJson()))
                    .response(PublicKeyCredential.parseRegistrationResponseJson(response.toString())).build());
            String id = result.getKeyId().getId().getBase64Url(), key = result.getPublicKeyCose().getBase64Url();
            if (id.isEmpty() || id.length() > 1364 || key.length() > 2048 || credentials.findByCredentialId(id).isPresent()) throw new Rejected();
        } catch (org.springframework.dao.DataAccessException e) { throw e; }
        catch (Exception e) { fail(limit); return null; }
        // Persistence failures must roll back activation rather than being treated as bad signatures.
        var credential = credentials.save(new WebAuthnCredential(user, result.getKeyId().getId().getBase64Url(),
                result.getPublicKeyCose().getBase64Url(), c.getDisplayName()));
        user.setWebauthnEnabled(true);
        challenges.consumeOutstanding(user.getId(), Instant.now());
        refresh.revokeAllForUser(user.getId());
        credentials.flush();
        return new Registered(credential.getId(), credential.getDisplayName());
    }

    private MfaLoginChallenge loginChallenge(User user, String hash, LoginAttemptLimit limit) {
        var c = challenges.findForUpdate(hash, user.getId()).orElse(null);
        if (c == null || c.getConsumedAt() != null || !Instant.now().isBefore(c.getExpiresAt())
                || !user.isWebauthnEnabled() || !TokenHashUtil.sha256(user.getPassword()).equals(c.getPasswordFingerprint())) fail(limit);
        return c;
    }
    private String tokenHash(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new Rejected();
        return TokenHashUtil.sha256(token);
    }
    public Options beginLogin(String token) {
        engine.relyingParty();
        String hash = tokenHash(token);
        Long owner = challenges.findOwnerId(hash).orElseThrow(Rejected::new);
        User user = users.findForAuthenticationUpdate(owner).orElseThrow(Rejected::new);
        var limit = limit(user); var login = loginChallenge(user, hash, limit);
        if (credentials.findAllByUserId(user.getId()).isEmpty()) fail(limit);
        var c = start(user, WebAuthnCeremony.Kind.LOGIN);
        c.setLoginTokenHash(hash); c.setExpiresAt(login.getExpiresAt());
        var request = assertion(user);
        try {
            c.setRequestJson(request.toJson()); ceremonies.saveAndFlush(c);
            return new Options(c.getId(), c.getExpiresAt(), publicKey(request.toCredentialsGetJson()), null);
        } catch (java.io.IOException e) { throw new IllegalStateException("Could not serialize WebAuthn request."); }
    }
    public AuthenticatedSession finishLogin(String token, UUID requestId, JsonNode response) {
        engine.relyingParty();
        String hash = tokenHash(token);
        Long owner = challenges.findOwnerId(hash).orElseThrow(Rejected::new);
        User user = users.findForAuthenticationUpdate(owner).orElseThrow(Rejected::new);
        var limit = limit(user); var login = loginChallenge(user, hash, limit);
        var c = consume(user, requestId, WebAuthnCeremony.Kind.LOGIN, limit);
        if (!hash.equals(c.getLoginTokenHash())) fail(limit);
        login.setConsumedAt(Instant.now());
        try { verifyAssertion(user, c.getRequestJson(), response); }
        catch (Exception e) { fail(limit); }
        return refresh.issueSession(user, refreshDays, true);
    }
}
