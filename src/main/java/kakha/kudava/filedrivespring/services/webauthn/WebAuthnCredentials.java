package kakha.kudava.filedrivespring.services.webauthn;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.*;
import kakha.kudava.filedrivespring.model.User;
import kakha.kudava.filedrivespring.model.WebAuthnCredential;
import kakha.kudava.filedrivespring.repository.*;
import org.springframework.stereotype.Component;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class WebAuthnCredentials implements CredentialRepository {
    private final UserRepository users;
    private final WebAuthnCredentialRepository credentials;
    public WebAuthnCredentials(UserRepository users, WebAuthnCredentialRepository credentials) {
        this.users = users; this.credentials = credentials;
    }
    public static ByteArray handle(User user) {
        UUID id = Objects.requireNonNull(user.getPublicUuid());
        return new ByteArray(ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array());
    }
    private ByteArray bytes(String value) {
        try { return ByteArray.fromBase64Url(value); }
        catch (Exception e) { throw new IllegalStateException("Invalid stored credential encoding."); }
    }
    private RegisteredCredential registered(WebAuthnCredential c) {
        return RegisteredCredential.builder().credentialId(bytes(c.getCredentialId()))
                .userHandle(handle(c.getUser())).publicKeyCose(bytes(c.getPublicKeyCose()))
                .signatureCount(0).build(); // Counter validation is explicitly disabled.
    }
    @Override public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String name) {
        return users.findByUsername(name).map(u -> credentials.findAllByUserId(u.getId()).stream()
                .map(c -> PublicKeyCredentialDescriptor.builder().id(bytes(c.getCredentialId())).build())
                .collect(Collectors.toSet())).orElseGet(Set::of);
    }
    @Override public Optional<ByteArray> getUserHandleForUsername(String name) {
        return users.findByUsername(name).map(WebAuthnCredentials::handle);
    }
    @Override public Optional<String> getUsernameForUserHandle(ByteArray handle) {
        if (handle.getBytes().length != 16) return Optional.empty();
        ByteBuffer b = ByteBuffer.wrap(handle.getBytes());
        return users.findByPublicUuid(new UUID(b.getLong(), b.getLong())).map(User::getUsername);
    }
    @Override public Optional<RegisteredCredential> lookup(ByteArray id, ByteArray handle) {
        return credentials.findByCredentialId(id.getBase64Url())
                .filter(c -> handle(c.getUser()).equals(handle)).map(this::registered);
    }
    @Override public Set<RegisteredCredential> lookupAll(ByteArray id) {
        return credentials.findByCredentialId(id.getBase64Url()).map(c -> Set.of(registered(c))).orElseGet(Set::of);
    }
}
