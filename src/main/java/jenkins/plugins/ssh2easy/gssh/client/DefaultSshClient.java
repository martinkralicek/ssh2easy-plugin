package jenkins.plugins.ssh2easy.gssh.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import jenkins.plugins.ssh2easy.gssh.GsshPluginException;
//import jenkins.plugins.ssh2easy.gssh.GsshUserInfo;
import jenkins.plugins.ssh2easy.gssh.ServerGroup;
import org.apache.commons.lang.StringEscapeUtils;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import java.io.File;
//import com.jcraft.jsch.UserInfo;

public class DefaultSshClient extends AbstractSshClient {

    public static final String SSH_BEY = "\nexit $?";

    private String ip;
    private int port;
    private String username;
    private String password;
    private String privatekey;

    public DefaultSshClient(String ip, int port, String username, String password, String privatekey) {
        this.ip = ip;
        this.port = port;
        this.username = username;
        this.password = password;
        this.privatekey = privatekey;
    }

    public DefaultSshClient(ServerGroup serverGroup, String ip) {
        this.port = serverGroup.getPort();
        this.username = serverGroup.getUsername();
        this.password = serverGroup.getPassword();
        this.privatekey = serverGroup.getPrivatekey();
        this.ip = ip;
    }

    public static SshClient newInstance(String ip, int port, String username, String password, String privatekey) {
        return new DefaultSshClient(ip, port, username, password, privatekey);
    }

    public static SshClient newInstance(ServerGroup group, String ip) {
        return new DefaultSshClient(group, ip);
    }

    public Session createSession(PrintStream logger) {
        JSch jsch = new JSch();
        Session session = null;
        String mode;
        boolean usePrikey = false;
        try {
            
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            
            if (!privatekey.isEmpty()) {
                usePrikey = true;
                if (!password.isEmpty()){
                    jsch.addIdentity(privatekey, password);
                    mode = "privatekey with password";
                } else {
                    jsch.addIdentity(privatekey);
                    mode = "privatekey without password";
                } 
            } else {
                mode = "only with password";
            }
            
            session = jsch.getSession(username, ip, port);
            
            if (!usePrikey)
            {
                session.setPassword(password);
            }
                       
            session.setConfig(config);
            session.setDaemonThread(false);
            session.connect();

            logger.println("Create ssh session success with " + username + "@" + ip + " to port: " + port + " mode: " + mode);
            
        } catch (Exception e) {
            logger.println("Create ssh session failed with " + username + "@" + ip + " to port: " + port);
            e.printStackTrace(logger);
            throw new GsshPluginException(e);
        }
        return session;
    }
    
    private int workFile(boolean download, PrintStream logger, String targetFile, String destLocation, String newFileName, InputStream fileContent){
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = createSession(logger);
            Channel channel = session.openChannel("sftp");
            channel.setOutputStream(logger, true);
            channel.setExtOutputStream(logger, true);
            channel.connect();
            sftp = (ChannelSftp) channel;
            sftp.setFilenameEncoding("UTF-8");
            
            if (!download) {
                sftp.cd(destLocation);
                sftp.put(fileContent, targetFile);
                logger.println("Upload local file [ " + targetFile + " ] to remote [ " + destLocation + newFileName + " ]");
            } else {
                sftp.get(targetFile, destLocation + "/" + newFileName);
                logger.println("Download remote file [ " + targetFile + " ] to local [ " + destLocation + newFileName + " ]");
            }
            return STATUS_SUCCESS;
             
        } catch (JSchException | SftpException e) {
            logger.println("[GSSH - SFTP] Exception:" + e.getMessage());
            e.printStackTrace(logger);
            throw new GsshPluginException(e);    
        } finally {
            closeSession(session, sftp);
        }
    }
    
    @Override
    public int uploadFile(PrintStream logger, String fileName, InputStream fileContent, String serverLocation) {
        return workFile(false, logger, fileName, serverLocation, "", fileContent);
    }

    @Override
    public int downloadFile(PrintStream logger, String remoteFile, String localFolder, String fileName) {
        return workFile(true, logger, remoteFile, localFolder, fileName, null);
    }

    @Override
    public int executeShell(PrintStream logger, String shell) {
        return executeCommand(logger, shell);
    }

    @Override
    public int executeCommand(PrintStream logger, String command) {

        Session session = null;
        ChannelExec channel = null;
        InputStream in = null;
        try {
            String wrapperCommand = wrapperInput(command);
            logger.write("execute below commands:".getBytes());
            logger.write(wrapperCommand.getBytes());
            logger.flush();
            session = createSession(logger);
            channel = (ChannelExec) session.openChannel("exec");
            channel.setOutputStream(logger, true);
            channel.setExtOutputStream(logger, true);
            channel.setPty(Boolean.FALSE);
            channel.setCommand(wrapperCommand);
            channel.connect();
            Thread.sleep(1000);
            while (true) {
                byte[] buffer = new byte[2048];
                int len = -1;
                in = channel.getInputStream();
                while (-1 != (len = in.read(buffer))) {
                    logger.write(buffer, 0, len);
                    logger.flush();
                }
                if (channel.isEOF()) {
                    break;
                }
                if (!channel.isConnected()) {
                    break;
                }
                if (channel.isClosed()) {
                    break;
                }
                Thread.sleep(1000);
            }
            int status = channel.getExitStatus();
            logger.println("shell exit status code --> " + status);
            return status;
        } catch (IOException e) {
            logger.println("[GSSH]-cmd Exception:" + e.getMessage());
            e.printStackTrace(logger);
            closeSession(session, channel);
            throw new GsshPluginException(e);

        } catch (JSchException e) {
            logger.println("[GSSH]-cmd Exception:" + e.getMessage());
            e.printStackTrace(logger);
            closeSession(session, channel);
            throw new GsshPluginException(e);
        } catch (InterruptedException e) {
            logger.println("[GSSH]-cmd Exception:" + e.getMessage());
            e.printStackTrace(logger);
            closeSession(session, channel);
            throw new GsshPluginException(e);
        } finally {
            if (null != in) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
            closeSession(session, channel);
        }
    }

    @Override
    public boolean testConnection(PrintStream logger) {
        try {
            Session session = createSession(logger);
            closeSession(session, null);
            return true;
        } catch (Exception e) {
            logger.println("test ssh connection failed!");
            e.printStackTrace(logger);
            return false;
        }
    }

    private void closeSession(Session session, Channel channel) {
        if (channel != null) {
            channel.disconnect();
            //channel = null;
        }
        if (session != null) {
            session.disconnect();
            //session = null;
        }
    }

    protected String wrapperInput(String input) {
        String output = fixIEIssue(input);
        return output + SSH_BEY;
    }

    /**
     * this is fix the IE issue that it's input shell /command auto add '<br>
     * ' if \n
     *
     * @param input
     * @return
     */
    private String fixIEIssue(String input) {
        return StringEscapeUtils.unescapeHtml(input);
    }

    public String getPrivatekey() {
        return privatekey;
    }

    public void setPrivatekey(String privatekey) {
        this.privatekey = privatekey;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "Server Info [" + this.ip + " ," + this.port + "," + this.username + "," + this.password + "]";
    }
}
