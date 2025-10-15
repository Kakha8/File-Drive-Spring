package kakha.kudava.sftpspring.services;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ServerSessionRegistry {

    public static final class SessionInfo {
        public final String sessionId;
        public final String username;
        public final String remote;     // optional (IP / client hint)
        public final Instant createdAt;
        private volatile Instant lastSeen;

        private SessionInfo(String id, String username, String remote) {
            this.sessionId = id;
            this.username = username;
            this.remote = remote;
            this.createdAt = Instant.now();
            this.lastSeen = this.createdAt;
        }
        public Instant getLastSeen() { return lastSeen; }
        private void touch() { this.lastSeen = Instant.now(); }
    }

    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public String create(String username, String remote) {
        Objects.requireNonNull(username, "username");
        String id = UUID.randomUUID().toString();
        sessions.put(id, new SessionInfo(id, username, remote));
        return id;
    }

    public boolean heartbeat(String sessionId) {
        SessionInfo s = sessions.get(sessionId);
        if (s == null) return false;
        s.touch();
        return true;
    }

    public void close(String sessionId) {
        sessions.remove(sessionId);
    }

    public List<SessionInfo> list() {
        return new ArrayList<>(sessions.values());
    }

    public boolean isUserConnected(String username) {
        if (username == null) return false;
        for(SessionInfo sessions : sessions.values()) {
            return sessions.username.equals(username);
        }
        return false;
    }
}