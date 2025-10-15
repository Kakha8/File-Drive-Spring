package kakha.kudava.sftpspring.services;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import kakha.kudava.sftpspring.model.User;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
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
    /** Simple user model */
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

    public synchronized void addDefaultUsers(List<kakha.kudava.sftpspring.model.User> users) {
        for (kakha.kudava.sftpspring.model.User user : users) {
            String password = user.getPassword();
            String username = user.getUsername();

            addUserIfAbsent(username, password, baseRoot.resolve(username));
        }
    }

    /** Start server with whatever users are configured (auto-seed if empty). */
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
}
