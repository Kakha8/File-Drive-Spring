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

    private SshServer sshd;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final int port = 8022;
   // private final String username = "user";
    //private final String password = "pass";
    private final Path rootDir = Paths.get("sftp-root");
    private final Path hostKeyFile = Paths.get("hostkey.ser");

    public synchronized void start(String username, String password) throws IOException {
        if (running.get()) {
            System.out.println("SFTP server already running on port " + port);
            return;
        }

        Files.createDirectories(rootDir);

        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(port);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKeyFile));

        PasswordAuthenticator pa = (u, p, session) -> username.equals(u) && password.equals(p);
        sshd.setPasswordAuthenticator(pa);

        sshd.setSubsystemFactories(Collections.singletonList(
                new SftpSubsystemFactory.Builder().build()
        ));
        sshd.setFileSystemFactory(new VirtualFileSystemFactory(rootDir));

        try {
            sshd.start(); // non-blocking
            running.set(true);
            System.out.println("SFTP server started on port " + port);
            System.out.println("Root directory: " + rootDir.toAbsolutePath());
            System.out.println("Login -> username: " + username + "  password: " + password);
        } catch (IOException e) {
            sshd = null;
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
