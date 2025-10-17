package kakha.kudava.sftpspring.controller.api;

import kakha.kudava.sftpspring.services.ServerSessionRegistry;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/sessions")
public class ServerSessionController {

    private final ServerSessionRegistry registry;

    public ServerSessionController(ServerSessionRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/create")
    public Map<String, Object> create(@RequestParam String username,
                                      @RequestParam(required = false) String remote) {
        String id = registry.create(username, remote);
        return Map.of("ok", true, "sessionId", id, "createdAt", Instant.now().toString());
    }

    @PostMapping("/heartbeat")
    public Map<String, Object> heartbeat(@RequestParam String sessionId) {
        boolean ok = registry.heartbeat(sessionId);
        return Map.of("ok", ok);
    }

    @PostMapping("/close")
    public Map<String, Object> close(@RequestParam String sessionId) {
        registry.close(sessionId);
        return Map.of("ok", true);
    }

    @GetMapping("/active")
    public Map<String, Object> active() {
        var list = registry.list().stream().map(s -> Map.of(
                "sessionId", s.sessionId,
                "username", s.username,
                "remote", s.remote,
                "createdAt", s.createdAt.toString(),
                "lastSeen", s.getLastSeen().toString()
        )).toList();

        return Map.of("count", list.size(), "sessions", list, "timestamp", Instant.now().toString());
    }
}
