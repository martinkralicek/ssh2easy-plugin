package jenkins.plugins.ssh2easy.gssh.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import jenkins.plugins.ssh2easy.gssh.GsshPluginException;
import jenkins.plugins.ssh2easy.gssh.ServerGroup;
import org.apache.log4j.Logger;
import com.trilead.ssh2.ChannelCondition;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.Session;
import java.util.concurrent.ExecutionException;

public class JenkinsSshClient extends DefaultSshClient {

    private static final Logger LOG = Logger.getLogger(JenkinsSshClient.class);

    public JenkinsSshClient(String ip, int port, String username, String password, String privatekey) {
        super(ip, port, username, password, privatekey);
    }

    public JenkinsSshClient(ServerGroup serverGroup, String ip) {
        super(serverGroup, ip);
    }

    public static SshClient newInstance(String ip, int port, String username, String password, String privatekey) {
        return new JenkinsSshClient(ip, port, username, password, privatekey);
    }

    public static SshClient newInstance(ServerGroup group, String ip) {
        return new JenkinsSshClient(group, ip);
    }

    public Connection getConnection() throws IOException {
        Connection conn = new Connection(this.getIp(), this.getPort());
        conn.connect();
        boolean isAuthenticated;
        String mode;

        if (!this.getPrivatekey().isEmpty()) {
            mode = "authenticateWithPublicKey";
            File fileKey = new File(this.getPrivatekey());
            isAuthenticated = conn.authenticateWithPublicKey(this.getUsername(), fileKey, this.getPassword());
        } else if (this.getPassword().isEmpty()) {
            mode = "authenticateWithNone";
            isAuthenticated = conn.authenticateWithNone(this.getUsername());
        } else {
            mode = "authenticateWithPassword";
            isAuthenticated = conn.authenticateWithPassword(this.getUsername(), this.getPassword());
        }

        if (!isAuthenticated) {
            throw new IOException("Authentication failed.");
        }
        LOG.info("Create ssh session success with " + getUsername() + "@" + getIp() + " to port: " + getPort() + " mode: " + mode);
        return conn;
    }

    @Override
    public int executeCommand(PrintStream logger, String command) {
        Connection conn = null;
        try {
            conn = getConnection();
        } catch (Exception e) {
            logger.println("Create ssh session failed with " + getUsername() + "@" + getIp() + " to port: " + getPort());
            e.printStackTrace(logger);
            throw new GsshPluginException(e);
        }
        Session session = null;
        String wrappedCommand = wrapperInput(command);
        try {
            session = conn.openSession();
            session.requestPTY("dumb");
            session.startShell();
            ExecutorService exec = Executors.newSingleThreadExecutor();
            Future<Boolean> task = exec.submit(new OutputTask(session, logger));
            PrintWriter out = new PrintWriter(session.getStdin());
            String commands[] = wrappedCommand.split("\n");
         
            for (String cmd : commands) {
                if ("".equals(cmd.trim())) {
                    continue;
                }
                out.println(cmd);
            }
                   
            out.close();
            task.get();
            exec.shutdown();
            int status = session.getExitStatus();
            logger.println("execute command exit status --> " + status);
            return status;
        } catch (IOException e) {
            String msg = "execute commds=[" + wrappedCommand + "]failed !";
            logger.println(msg);
            e.printStackTrace(logger);
            throw new GsshPluginException(msg, e);
        } catch (InterruptedException e) {
            String msg = "execute commds=[" + wrappedCommand + "]failed !";
            logger.println(msg);
            e.printStackTrace(logger);
            throw new GsshPluginException(msg, e);
        } catch (ExecutionException e) {
            String msg = "execute commds=[" + wrappedCommand + "]failed !";
            logger.println(msg);
            e.printStackTrace(logger);
            throw new GsshPluginException(msg, e);
        } finally {
            if (null != session) {
                session.close();
            }
            if (null != conn) {
                conn.close();
            }
        }
    }

    class OutputTask implements Callable<Boolean> {

        public boolean execute() throws IOException, InterruptedException {
            InputStream stdout = session.getStdout();
            InputStream stderr = session.getStderr();
            byte[] buffer = new byte[8192];
            boolean result = true;
            while (true) {
                if ((stdout.available() == 0) && (stderr.available() == 0)) {
                    int conditions = session.waitForCondition(
                            ChannelCondition.STDERR_DATA
                            | ChannelCondition.STDOUT_DATA
                            | ChannelCondition.EXIT_STATUS, 0);
                    if ((conditions & ChannelCondition.TIMEOUT) != 0) {
                        logger.println("wait timeout and exit now !");
                        break;
                    }
                    if ((conditions & ChannelCondition.EXIT_STATUS) != 0) {
                        break;
                    }
                }

                while (stdout.available() > 0) {
                    int len = stdout.read(buffer);
                    if (len > 0) {
                        logger.write(buffer, 0, len);
                    }
                }

                while (stderr.available() > 0) {
                    int len = stderr.read(buffer);
                    if (len > 0) {
                        logger.write(buffer, 0, len);
                    }
                    result = false;
                }
                if (!result) {
                    break;
                }
            }

            logger.println("####################################");
            return result;
        }

        @Override
        public Boolean call() throws Exception {
            Thread.sleep(2000);
            return execute();
        }

        private final PrintStream logger;
        private final Session session;

        public OutputTask(Session session, PrintStream logger) {
            super();
            this.session = session;
            this.logger = logger;
        }
    }
}
