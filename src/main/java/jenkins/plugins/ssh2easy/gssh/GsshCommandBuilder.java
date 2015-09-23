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
import java.io.PrintStream;
import java.util.logging.Logger;
import jenkins.plugins.ssh2easy.gssh.client.SshClient;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class GsshCommandBuilder extends Builder {

    public static final Logger LOGGER = Logger.getLogger(GsshCommandBuilder.class.getName());
    private boolean disable;
    private String serverInfo;
    private String groupName;
    private String ip;
    private String shell;

    public GsshCommandBuilder() {
    }

    @DataBoundConstructor
    public GsshCommandBuilder(boolean disable, String serverInfo, String shell) {
        this.disable = disable;
        this.serverInfo = serverInfo;
        this.shell = shell;
        this.ip = Server.parseIp(this.serverInfo);
        this.groupName = Server.parseServerGroupName(this.serverInfo);
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
        // This is where you 'build' the project.
        SshClient sshHandler = GsshBuilderWrapper.DESCRIPTOR.getSshClient(getGroupName(), getIp());
        int exitStatus = sshHandler.executeCommand(logger, shell);
        GsshBuilderWrapper.printSplit(logger);
        return exitStatus == SshClient.STATUS_SUCCESS;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
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

    public String getShell() {
        return shell;
    }

    public void setShell(String shell) {
        this.shell = shell;
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

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @SuppressWarnings("rawtypes")
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.SSHCOMMAND_DisplayName();
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
