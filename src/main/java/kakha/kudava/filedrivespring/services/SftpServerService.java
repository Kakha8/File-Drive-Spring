package kakha.kudava.filedrivespring.services;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;
import jdk.jfr.Event;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SftpServerService {

    private final SftpSessionRegistry sessionRegistry;

    public SftpServerService(SftpSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    private static final AttributeRepository.AttributeKey<String> REGISTRY_SESSION_ID = new AttributeRepository.AttributeKey<>();

    public static final class User {
        private final String password;
        private final Path home;

        public User(String password, Path home) {
            this.password = Objects.requireNonNull(password, "password");
            this.home = Objects.requireNonNull(home, "home").toAbsolutePath().normalize();
        }
        public String password() { return password; }
        public Path home() { return home; }
        @Override public String toString() {
            return "User{home=" + home + "}";
        }
    }

    private volatile SshServer sshd;
    private volatile VirtualFileSystemFactory vfs;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Config
    private final int port = 8022;
    private final Path hostKeyFile = Paths.get("hostkey.ser").toAbsolutePath().normalize();
    private final Path baseRoot = Paths.get("sftp-root").toAbsolutePath().normalize();

    private final Map<String, User> users = new ConcurrentHashMap<>();

/*    @PostConstruct
    public void addDefaultUsersOnInit() {
        addDefaultUsers(); // idempotent
    }*/

    // Public API ------------------------------------------------------------

    public synchronized void addDefaultUsers(List<kakha.kudava.filedrivespring.model.User> users) {
        for (kakha.kudava.filedrivespring.model.User user : users) {
            String password = user.getPassword();
            String username = user.getUsername();

            addUserIfAbsent(username, password, baseRoot.resolve(username));
        }
    }

    public synchronized void start() throws IOException {
        if (running.get()) {
            System.out.println("SFTP server already running on port " + port);
            return;
        }

        for (User u : users.values()) {
            Files.createDirectories(u.home());
        }

        SshServer server = SshServer.setUpDefaultServer();
        server.setPort(port);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKeyFile));

        PasswordAuthenticator pa = (username, password, session) -> {
            User u = users.get(username);
            return u != null && Objects.equals(u.password(), password);
        };
        server.setPasswordAuthenticator(pa);

        server.setSubsystemFactories(Collections.singletonList(
                new SftpSubsystemFactory.Builder().build()
        ));

        VirtualFileSystemFactory vfsFactory = new VirtualFileSystemFactory();
        for (Map.Entry<String, User> e : users.entrySet()) {
            vfsFactory.setUserHomeDir(e.getKey(), e.getValue().home());
        }
        vfsFactory.setDefaultHomeDir(getDefaultHome());
        server.setFileSystemFactory(vfsFactory);

        // ✅ ADDED: Track authenticated users / disconnects and update SftpSessionRegistry
        server.addSessionListener(new SessionListener() {

            @Override
            public void sessionCreated(Session session) {
                // Optional: log new TCP session (not authenticated yet)
                // System.out.println("🔌 New session created from: " + session.getIoSession().getRemoteAddress());
            }

            @Override
            public void sessionEvent(Session session, Event event) {
                if (event != Event.Authenticated) return;
                if (!(session instanceof ServerSession ss)) return;

                // Prevent double-registration if Authenticated event fires more than once
                if (ss.getAttribute(REGISTRY_SESSION_ID) != null) return;

                String username = ss.getUsername();
                String remote = String.valueOf(ss.getIoSession().getRemoteAddress());

                // Register this authenticated session in your app's registry
                String id = sessionRegistry.create(username, remote);

                // Store registry id on the SSH session so we can close it later
                ss.setAttribute(REGISTRY_SESSION_ID, id);

                // log
                System.out.println("User connected: " + username + " id=" + id);
                System.out.println("Registry state after server start: " + sessionRegistry.list());

            }

            @Override
            public void sessionClosed(Session session) {
                if (!(session instanceof ServerSession ss)) return;

                String id = ss.getAttribute(REGISTRY_SESSION_ID);
                if (id != null) {
                    sessionRegistry.close(id);
                }

                // Optional: log
                // System.out.println("❌ User disconnected: " + ss.getUsername() + " id=" + id);
            }
        });

        try {
            server.start(); // non-blocking
            this.sshd = server;
            this.vfs = vfsFactory;
            running.set(true);
            System.out.println("SFTP server started on port " + port);
            users.forEach((name, u) ->
                    System.out.println("  " + name + "  home=" + u.home()));
        } catch (IOException e) {
            this.sshd = null;
            this.vfs = null;
            running.set(false);
            throw e;
        }
    }

    public synchronized void stop(boolean immediately) {
        if (!running.get() || sshd == null) return;
        try {
            System.out.println("Stopping SFTP server...");
            sshd.stop(immediately);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            running.set(false);
            sshd = null;
            vfs = null;
        }
    }

    public boolean isRunning() { return running.get(); }

    public Map<String, String> listUsers() {
        Map<String, String> out = new LinkedHashMap<>();
        users.forEach((u, info) -> out.put(u, info.home().toString()));
        return out;
    }

    public synchronized void addUser(String username, String password, Path home) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(home, "home");
        Path norm = home.toAbsolutePath().normalize();
        users.put(username, new User(password, norm));
        try { Files.createDirectories(norm); }
        catch (IOException e) { throw new RuntimeException("Failed to create home for " + username + ": " + norm, e); }

        if (running.get() && vfs != null) {
            vfs.setUserHomeDir(username, norm);
        }
    }

    public synchronized void removeUser(String username) {
        users.remove(username);
        if (running.get() && vfs != null) {
            vfs.setUserHomeDir(username, getDefaultHome());
        }
    }

    @PreDestroy
    public void onShutdown() { stop(true); }

    // Helpers ---------------------------------------------------------------

    private void addUserIfAbsent(String username, String password, Path home) {
        users.computeIfAbsent(username, u -> {
            Path norm = home.toAbsolutePath().normalize();
            try { Files.createDirectories(norm); }
            catch (IOException e) { throw new RuntimeException("Failed to create home for " + u + ": " + norm, e); }
            if (vfs != null) vfs.setUserHomeDir(username, norm);
            return new User(password, norm);
        });
    }

    private Path getDefaultHome() {
        return users.values().stream()
                .findFirst()
                .map(User::home)
                .orElse(baseRoot.resolve("default").toAbsolutePath().normalize());
    }

    public boolean checkAdminRole(HttpSession session){
        if(session.getAttribute("ROLE") != null){
            if (session.getAttribute("ROLE").toString().contains("admin"))
                return true;
            else
                return false;
        }
        return false;
    }
}
