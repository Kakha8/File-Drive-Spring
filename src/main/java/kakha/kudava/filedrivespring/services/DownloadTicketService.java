package kakha.kudava.filedrivespring.services;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DownloadTicketService {
    private static final long LIFETIME_SECONDS = 120;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    public String issue(String username, Kind kind, List<Long> fileIds, List<Long> folderIds) {
        purgeExpired();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(value, new Ticket(username, kind, List.copyOf(fileIds), List.copyOf(folderIds), Instant.now().plusSeconds(LIFETIME_SECONDS)));
        return value;
    }

    public Ticket peek(String value) {
        Ticket ticket = tickets.get(value);
        if (ticket != null && ticket.expiresAt().isAfter(Instant.now())) return ticket;
        if (value != null) tickets.remove(value);
        return null;
    }

    public Ticket consume(String value) {
        Ticket ticket = value == null ? null : tickets.remove(value);
        return ticket != null && ticket.expiresAt().isAfter(Instant.now()) ? ticket : null;
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    public enum Kind { FILE, FOLDER, ZIP }

    public record Ticket(String username, Kind kind, List<Long> fileIds, List<Long> folderIds, Instant expiresAt) {}
}
