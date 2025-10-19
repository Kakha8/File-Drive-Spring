package kakha.kudava.sftpspring.services;

import com.jcraft.jsch.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

@Service
public class SftpClientService {

    private final SftpSessionRegistry registry;

    private final Map<String, String> serverSessionIds = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> heartbeats = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sftp-heartbeats");
                t.setDaemon(true);
                return t;
            });

    public SftpClientService(SftpSessionRegistry registry) {
        this.registry = registry;
    }


    public record SftpItem(String name, boolean directory, long size, long mtimeEpochSec) {}
    public record DirListing(String pwd, java.util.List<SftpItem> items) {}

    public record Connection(ChannelSftp channel, String serverSessionId) {}

    // --- Connect / Disconnect ----------------------------------------------

    public synchronized ChannelSftp connectSFTP(String username, String password) throws JSchException {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");

        final String host = "localhost";
        final int port = 8022;

        JSch jsch = new JSch();
        Session jsession = jsch.getSession(username, host, port);
        jsession.setPassword(password);

        java.util.Properties cfg = new java.util.Properties();
        cfg.put("StrictHostKeyChecking", "no");
        jsession.setConfig(cfg);

        jsession.connect(5000);

        Channel channel = jsession.openChannel("sftp");
        channel.connect(5000);
        ChannelSftp sftp = (ChannelSftp) channel;


        String remoteHint = host + ":" + port;
        String sid = registry.create(username, remoteHint);
        serverSessionIds.put(username, sid);

        //  Start heartbeat every ~25s to keep session active
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
                () -> registry.heartbeat(sid),
                25, 25, TimeUnit.SECONDS
        );
        heartbeats.put(username, task);

        System.out.println("Connected as " + username + " (serverSessionId=" + sid + ")");
        return sftp;
    }


    public synchronized void disconnectSFTP(String username, ChannelSftp sftp) {
        // 1) Close JSch channel + session
        if (sftp != null) {
            try { sftp.disconnect(); } catch (Exception ignored) {}
            try {
                Session sess = sftp.getSession();
                if (sess != null && sess.isConnected()) sess.disconnect();
            } catch (Exception ignored) {}
        }

        // Stop heartbeat & close server-wide session
        ScheduledFuture<?> hb = heartbeats.remove(username);
        if (hb != null) hb.cancel(true);

        String sid = serverSessionIds.remove(username);
        if (sid != null) registry.close(sid);

        System.out.println("Disconnected: " + username);
    }


    public synchronized void disconnectSFTP(String username) {
        ScheduledFuture<?> hb = heartbeats.remove(username);
        if (hb != null) hb.cancel(true);
        String sid = serverSessionIds.remove(username);
        if (sid != null) registry.close(sid);
        System.out.println("Disconnected (server session only): " + username);
    }


    public synchronized DirListing showDir(ChannelSftp sftp) throws SftpException {
        String pwd = sftp.pwd();

        @SuppressWarnings("unchecked")
        java.util.Vector<ChannelSftp.LsEntry> list =
                (java.util.Vector<ChannelSftp.LsEntry>) sftp.ls(".");

        java.util.List<SftpItem> items = new java.util.ArrayList<>(list.size());
        for (ChannelSftp.LsEntry e : list) {
            String name = e.getFilename();
            if (".".equals(name) || "..".equals(name)) continue;

            SftpATTRS a = e.getAttrs();
            boolean isDir = a != null && a.isDir();
            long size = (a == null) ? 0L : a.getSize();
            long mtime = (a == null) ? 0L : a.getMTime(); // seconds since epoch

            items.add(new SftpItem(name, isDir, size, mtime));
        }

        System.out.println("PWD = " + pwd + " | " + items.size() + " items");
        return new DirListing(pwd, items);
    }

    public boolean isConnected(ChannelSftp sftp) {
        try {
            return sftp != null
                    && sftp.isConnected()
                    && !sftp.isClosed()
                    && sftp.getSession() != null
                    && sftp.getSession().isConnected();
        } catch (JSchException e) {
            return false;
        }
    }

    public String getTime() {
        LocalDateTime myDateObj = LocalDateTime.now();
        DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        return myDateObj.format(myFormatObj);
    }
}
