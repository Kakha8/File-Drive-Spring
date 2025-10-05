package kakha.kudava.sftpspring.sftp;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
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

    public synchronized void uploadFile(String user, String password) {
        String host = "localhost";
        int port = 8022;
/*        String user = "user";
        String password = "pass";*/

        String localToUpload = "test.txt";       // must exist locally
        String remoteToDownload = "test.txt";    // must exist on server

        Session session = null;
        ChannelSftp sftp = null;

        try {
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

            // Show working dir
            String pwd = sftp.pwd();
            System.out.println("PWD = " + pwd);

            // 3) List files in current dir
            System.out.println("Listing:");
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> list = sftp.ls(".");
            for (ChannelSftp.LsEntry e : list) {
                System.out.println(" - " + e.getFilename());
            }

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
    }

}
