package kakha.kudava.filedrivespring.model;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop", showSql = false)
class WebAuthnCredentialPersistenceTests {
    @Autowired EntityManager em;

    private User user(String name) {
        User user = new User();
        user.setUsername(name);
        user.setPassword("test-hash");
        user.setRole(User.Role.USER);
        em.persist(user);
        em.flush();
        return user;
    }

    private WebAuthnCredential save(User user, String id) {
        // Encoding fixtures only; a real registration must validate the COSE key.
        WebAuthnCredential value = new WebAuthnCredential(user, id, "AQID", "ESP32");
        em.persist(value);
        em.flush();
        return value;
    }

    @Test
    void persistsMultipleCredentialsAndTreatsIdsAsCaseSensitive() {
        User user = user("credential-owner");
        WebAuthnCredential first = save(user, "AAAA");
        WebAuthnCredential second = save(user, "aaaa");
        assertNotEquals(first.getId(), second.getId());
        assertNotNull(first.getCreatedAt());
        assertNull(first.getLastUsedAt());
        em.clear();
        assertEquals("AAAA", em.find(WebAuthnCredential.class, first.getId()).getCredentialId());
        assertEquals("aaaa", em.find(WebAuthnCredential.class, second.getId()).getCredentialId());
    }

    @Test
    void rejectsDuplicateCredentialAcrossAccounts() {
        User first = user("first-owner"), second = user("second-owner");
        save(first, "AQID");
        assertThrows(org.hibernate.exception.ConstraintViolationException.class, () -> save(second, "AQID"));
    }

    @Test
    void rejectsBlankDisplayNameOnPersistence() {
        User user = user("blank-name");
        assertThrows(ConstraintViolationException.class, () -> {
            em.persist(new WebAuthnCredential(user, "AQID", "AQID", "   "));
            em.flush();
        });
    }

    @Test
    void rejectsInvalidEncodedFieldsAndOverlongNames() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var invalid = new WebAuthnCredential(new User(), "bad+id=", "bad/key=", "x".repeat(101));
            var fields = validator.validate(invalid).stream()
                    .map(v -> v.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet());
            assertEquals(java.util.Set.of("credentialId", "publicKeyCose", "displayName"), fields);
        }
    }

    @Test
    void requiresAnOwner() {
        assertThrows(ConstraintViolationException.class, () -> save(null, "AQID"));
    }
}
