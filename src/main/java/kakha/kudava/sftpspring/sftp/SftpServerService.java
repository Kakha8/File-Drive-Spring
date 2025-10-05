package kakha.kudava.sftpspring.sftp;

import jakarta.annotation.PreDestroy;
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
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SftpServerService {
    private volatile SshServer sshd;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // You can parameterize these later if you want to pass creds from a form
    private final int port = 8022;
    private final String defaultUsername = "user";
    private final String defaultPassword = "pass";
    private final Path rootDir = Paths.get("sftp-root");
    private final Path hostKeyFile = Paths.get("hostkey.ser");

    /** Start with default creds (user/pass). */
    public synchronized void start() throws IOException {
        start(this.defaultUsername, this.defaultPassword);
    }

    /** Start with provided creds */
    public synchronized void start(String username, String password) throws IOException {
        if (running.get()) {
            System.out.println("SFTP server already running on port " + port);
            return;
        }

        Path rootAbs = rootDir.toAbsolutePath().normalize();
        Path hostKeyAbs = hostKeyFile.toAbsolutePath().normalize();
        Files.createDirectories(rootAbs);

        SshServer server = SshServer.setUpDefaultServer();
        server.setPort(port);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKeyAbs));

        // Simple username/password auth against the provided creds
        PasswordAuthenticator pa = (u, p, session) -> username.equals(u) && password.equals(p);
        server.setPasswordAuthenticator(pa);

        // Enable SFTP
        server.setSubsystemFactories(Collections.singletonList(
                new SftpSubsystemFactory.Builder().build()
        ));

        // *** CRITICAL: Map VFS home explicitly so "/" and "." resolve correctly
        VirtualFileSystemFactory vfs = new VirtualFileSystemFactory();
        vfs.setUserHomeDir(username, rootAbs);   // same as your working standalone server
        vfs.setDefaultHomeDir(rootAbs);          // optional fallback
        server.setFileSystemFactory(vfs);

        try {
            server.start(); // non-blocking
            this.sshd = server;
            running.set(true);
            System.out.println("SFTP server started on port " + port);
            System.out.println("Root directory: " + rootAbs);
            System.out.println("Login -> username: " + username + "  password: " + password);
        } catch (IOException e) {
            this.sshd = null;
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
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    @PreDestroy
    public void onShutdown() {
        stop(true);
    }
}
