package kakha.kudava.sftpspring.sftp;

import com.jcraft.jsch.*;
import org.apache.sshd.server.SshServer;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SftpClientService {
    private SshServer sshd;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public record SftpItem(String name, boolean directory, long size, long mtimeEpochSec) {}
    public record DirListing(String pwd, java.util.List<SftpItem> items) {}


/*    public synchronized void uploadFile(String user, String password) {


        String localToUpload = "test.txt";       // must exist locally
        String remoteToDownload = "test.txt";    // must exist on server



        try {


            // 4) Upload (local -> remote)
            try (FileInputStream fis = new FileInputStream(localToUpload)) {
                String remoteName = "uploaded_" + localToUpload;
                sftp.put(fis, remoteName, ChannelSftp.OVERWRITE);
                System.out.println("Uploaded -> " + remoteName);
            } catch (Exception ex) {
                System.out.println("Upload skipped / failed: " + ex.getMessage());
            }

            // 5) Download (remote -> local)
            try (FileOutputStream fos = new FileOutputStream("downloaded_" + remoteToDownload)) {
                sftp.get(remoteToDownload, fos);
                System.out.println("Downloaded -> downloaded_" + remoteToDownload);
            } catch (Exception ex) {
                System.out.println("Download skipped / failed: " + ex.getMessage());
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (sftp != null) sftp.disconnect();
            if (session != null) session.disconnect();
        }
    }*/

    public synchronized ChannelSftp connectSFTP(String user, String password) throws JSchException {
        String host = "localhost";
        int port = 8022;
/*        String user = "user";
        String password = "pass";*/

        Session session = null;
        ChannelSftp sftp = null;

        // 1) Connect
        JSch jsch = new JSch();

        // For real use, set a known_hosts file instead of disabling checks:
        // jsch.setKnownHosts("known_hosts");
        session = jsch.getSession(user, host, port);
        session.setPassword(password);

        // demo-only: skip host key checking
        java.util.Properties cfg = new java.util.Properties();
        cfg.put("StrictHostKeyChecking", "no");
        session.setConfig(cfg);

        session.connect(5000);
        System.out.println("Connected.");

        // 2) Open SFTP channel
        Channel channel = session.openChannel("sftp");
        channel.connect(5000);
        sftp = (ChannelSftp) channel;
        return sftp;
    }

    public synchronized void disconnectSFTP(String username) throws JSchException {
        System.out.println("Disconnecting: " + username);
        JSch jsch = new JSch();
        Session session = jsch.getSession(username, "localhost", 8022);
        session.disconnect();
    }
    public synchronized DirListing showDir(ChannelSftp sftp) throws SftpException {
        String pwd = sftp.pwd();

        @SuppressWarnings("unchecked")
        java.util.Vector<ChannelSftp.LsEntry> list = (java.util.Vector<ChannelSftp.LsEntry>) sftp.ls(".");

        java.util.List<SftpItem> items = new java.util.ArrayList<>(list.size());
        for (ChannelSftp.LsEntry e : list) {
            String name = e.getFilename();
            if (".".equals(name) || "..".equals(name)) continue; // hide self/parent

            SftpATTRS a = e.getAttrs();
            boolean isDir = a != null && a.isDir();
            long size = (a == null) ? 0L : a.getSize();
            long mtime = (a == null) ? 0L : a.getMTime(); // seconds since epoch

            items.add(new SftpItem(name, isDir, size, mtime));
        }

        // (optional) still log if you like
        System.out.println("PWD = " + pwd);
        System.out.println("Listing: " + items.size() + " items");

        return new DirListing(pwd, items);
    }

    public boolean isConnected(ChannelSftp sftp) {
        try {
            return sftp != null
                    && sftp.isConnected()
                    && !sftp.isClosed()
                    && sftp.getSession() != null
                    && sftp.getSession().isConnected();
        } catch (com.jcraft.jsch.JSchException e) {
            return false;
        }
    }

}
