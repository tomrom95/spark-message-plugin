package com.cisco.sparkmessage;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import jenkins.python.DataConvertor;
import jenkins.python.PythonExecutor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class SparkMessaging extends Notifier {

    private final String rooms;
    private final String processName;

    @DataBoundConstructor
    public SparkMessaging(String rooms, String processName) {
        this.rooms = rooms;
        this.processName = processName;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {

        String machineUser = getDescriptor().getMachineUser();
        String machinePassword = getDescriptor().getMachinePassword();

        if (isEmpty(rooms)) {
            listener.error(
                    "No rooms specified");
            return true;
        }

        if (isEmpty(machineUser) || isEmpty(machinePassword)) {
            listener.error(
                    "Machine Account credentials required");
            return true;
        }

        String message = "";
        if (!isEmpty(processName)) {
            message = processName;
        } else {
            message = build.getProject().getDisplayName();
        }

        if (build.getResult() == Result.FAILURE || build.getResult() == Result.UNSTABLE) {
            message += " has failed";
        } else {
            message += " has successfully completed";
        }
        listener.getLogger().println(message);
        PythonExecutor pexec = new PythonExecutor(this);
        if (pexec == null) {
            listener.getLogger().println("Whoops this is null");
            return true;
        }
        boolean result = pexec.execPythonBool("test", message);

        return true;
    }

    @Override
    public boolean prebuild(AbstractBuild build, BuildListener listener) {
        String machineUser = getDescriptor().getMachineUser();
        String machinePassword = getDescriptor().getMachinePassword();

        if (isEmpty(rooms)) {
            listener.error(
                    "No rooms specified");
            return true;
        }

        if (isEmpty(machineUser) || isEmpty(machinePassword)) {
            listener.error(
                    "Machine Account credentials required");
            return true;
        }

        String message = "";
        if (!isEmpty(processName)) {
            message = processName;
        } else {
            message = build.getProject().getDisplayName();
        }
        message = "Starting " + message;
        listener.getLogger().println(message);
        PythonExecutor pexec = new PythonExecutor(this);
        if (pexec == null) {
            listener.getLogger().println("Whoops this is null");
            return true;
        }
        boolean result = pexec.execPythonBool("test", message);
        return true;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    public String getRooms() {
        return rooms;
    }

    public String getProcessName() {
        return processName;
    }

    /**
     * Descriptor for {@link SMSNotification}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/hudson/plugin/hello_world/SMSNotification/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private String machineUser;
        private String machinePassword;

        public DescriptorImpl() {
            super(SparkMessaging.class);
            load();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Spark Messaging";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            machineUser = formData.getString("machineUser");
            machinePassword = formData.getString("machinePassword");
            save();
            return super.configure(req,formData);
        }

        public String getMachineUser() {
            return machineUser;
        }

        public void setMachineUser(String machineUser) {
            this.machineUser = machineUser;
        }

        public String getMachinePassword() {
            return machinePassword;
        }

        public void setMachinePassword(String machinePassword) {
            this.machinePassword = machinePassword;
        }

        public FormValidation doRoomCheck(@QueryParameter String roomString) throws IOException, ServletException {
            if (roomString == null || roomString.trim().length() == 0) {
                return FormValidation.warning("Spark rooms required");
            }
            return FormValidation.ok();
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }

}

