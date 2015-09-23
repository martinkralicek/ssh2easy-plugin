package jenkins.plugins.ssh2easy.gssh;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import java.io.File;
import java.io.PrintStream;
import java.util.logging.Logger;
import jenkins.plugins.ssh2easy.gssh.client.SshClient;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class GsshFtpDownloadBuilder extends Builder {

    public static final Logger LOGGER = Logger.getLogger(GsshShellBuilder.class.getName());
    private boolean disable;
    private String serverInfo;
    private String groupName;
    private String ip;
    private String remoteFile;
    private String localFolder;
    private String fileName;

    public GsshFtpDownloadBuilder() {
    }

    @DataBoundConstructor
    public GsshFtpDownloadBuilder(boolean disable, String serverInfo, String remoteFile, String localFolder, String fileName) {              
        this.disable = disable;
        this.serverInfo = serverInfo;
        this.ip = Server.parseIp(this.serverInfo);
        this.groupName = Server.parseServerGroupName(this.serverInfo);
        this.remoteFile = remoteFile;
        this.fileName = fileName;
        this.localFolder = localFolder;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        PrintStream logger = listener.getLogger();
        GsshBuilderWrapper.printSplit(logger);
        if (isDisable()) {
            logger.println("current step is disabled , skip to execute");
            return true;
        }
        logger.println("execute on server -- " + getServerInfo());
             
        SshClient sshClient = GsshBuilderWrapper.DESCRIPTOR.getSshClient(getGroupName(), getIp());
        int exitStatus;
        try {
            String path = localFolder;
            
            if (null == fileName || fileName.trim().equals("")) {
                File file = new File(getRemoteFile());
                fileName = file.getName();
            }
            
            if (!new File(path + File.separator + fileName).isAbsolute()) {
                logger.println("localFolder is relative");
                String JENKINS_HOME = System.getenv("JENKINS_HOME");        
                if (JENKINS_HOME != null | !JENKINS_HOME.isEmpty()){            
                    logger.println("JENKINS_HOME will be used");
                    path = JENKINS_HOME + File.separator + path;
                }
            }
            logger.println("localFolder: " + path);
            
            exitStatus = sshClient.downloadFile(logger, remoteFile, path, fileName);
            GsshBuilderWrapper.printSplit(logger);
        } catch (Exception e) {
            return false;
        }
        return exitStatus == SshClient.STATUS_SUCCESS;
    }

    public String getServerInfo() {
        return serverInfo;
    }

    public void setServerInfo(String serverInfo) {
        this.serverInfo = serverInfo;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public boolean isDisable() {
        return disable;
    }

    public void setDisable(boolean disable) {
        this.disable = disable;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getRemoteFile() {
        return remoteFile;
    }

    public void setRemoteFile(String remoteFile) {
        this.remoteFile = remoteFile;
    }

    public String getLocalFolder() {
        return localFolder;
    }

    public void setLocalFolder(String localFolder) {
        this.localFolder = localFolder;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @SuppressWarnings("rawtypes")
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.SSHFTPDOWNLOAD_DisplayName();
        }

        @Override
        public Builder newInstance(StaplerRequest req, JSONObject formData)
                throws Descriptor.FormException {
            return req.bindJSON(this.clazz, formData);
        }

        public ListBoxModel doFillServerInfoItems() {
            ListBoxModel m = new ListBoxModel();
            for (Server server : GsshBuilderWrapper.DESCRIPTOR.getServers()) {
                m.add(server.getServerInfo());
            }
            return m;
        }
    }
}
