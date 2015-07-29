package com.cisco.sparkmessage;
import hudson.EnvVars;
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
import java.util.Map;

/*
    Main class for handing Spark Messaging. Extends the prebuild Notifier
    class from Hudson.
*/
public class SparkMessaging extends Notifier {

    /*
        Values from build configuration form
    */
    private final String rooms;
    private final String startMessage;
    private final String failMessage;
    private final String successMessage;
    private final boolean start;
    private final boolean fail;
    private final boolean success;
    private final boolean addUrl;

    @DataBoundConstructor
    public SparkMessaging(String rooms,
                          String startMessage, String failMessage, String successMessage,
                          boolean start, boolean fail, boolean success,
                          boolean addUrl) {
        this.rooms = rooms;
        this.startMessage = startMessage;
        this.failMessage = failMessage;
        this.successMessage = successMessage;
        this.start = start;
        this.fail = fail;
        this.success = success;
        this.addUrl = addUrl;
    }

    public SparkMessaging() {
        /*
            Blank constructor mostly used for the pyexec call in doAddMachine
        */
        this.rooms = "";
        this.startMessage = "";
        this.failMessage = "";
        this.successMessage = "";
        this.start = false;
        this.fail = false;
        this.success = false;
        this.addUrl = false;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        /*
            Method called at end of build to message about success/failures
        */
        if (fail || success) {

            String machineUser = getDescriptor().getMachineUser();
            String machinePassword = getDescriptor().getMachinePassword();
            String basicAuth = getDescriptor().getBasicAuth();
            String orgId = getDescriptor().getOrgId();

            if (isEmpty(rooms)) {
                listener.getLogger().println(
                        "Error Messaging Spark Room: No rooms specified");
                return true;
            }

            if (isEmpty(machineUser) || isEmpty(machinePassword) || isEmpty(basicAuth) || isEmpty(orgId)) {
                listener.getLogger().println(
                        "Error Messaging Spark Room: Machine Account credentials required");
                return true;
            }

            String message = "";
            if (addUrl){
                message = " " + build.getAbsoluteUrl();
            }

            if (build.getResult() == Result.FAILURE || build.getResult() == Result.UNSTABLE) {
                if (this.fail) {
                    if (!isEmpty(failMessage)){
                        message = replaceVariables(failMessage, build, listener) + message;
                    } else{
                        message = build.getProject().getDisplayName() + " has failed" + message;
                    }
                    sendMessage(message, machineUser, machinePassword, basicAuth, orgId, listener);
                } 
            } else {
                if (this.success) {
                    if (!isEmpty(successMessage)){
                        message = replaceVariables(successMessage, build, listener) + message;
                    } else{
                        message = build.getProject().getDisplayName() + " has succeeded" + message;
                    }
                    sendMessage(message, machineUser, machinePassword, basicAuth, orgId, listener);
                }
            }
        }
        return true;
    }

    @Override
    public boolean prebuild(AbstractBuild build, BuildListener listener) {
        /*
            Called before build process begins about build start
        */
        if (start) {
            String machineUser = getDescriptor().getMachineUser();
            String machinePassword = getDescriptor().getMachinePassword();
            String basicAuth = getDescriptor().getBasicAuth();
            String orgId = getDescriptor().getOrgId();
            if (isEmpty(rooms)) {
                listener.getLogger().println(
                        "Error Messaging Spark Room: No rooms specified");
                return true;
            }

            if (isEmpty(machineUser) || isEmpty(machinePassword) || isEmpty(basicAuth) || isEmpty(orgId)) {
                listener.getLogger().println(
                        "Error Messaging Spark Room: Machine Account credentials required");
                return true;
            }

            String message = "";
            if (!isEmpty(startMessage)) {
                message = replaceVariables(startMessage, build, listener);
            } else {
                message = "Starting " + build.getProject().getDisplayName();
            }
            if (addUrl){
                message += " " + build.getAbsoluteUrl() + "console";
            }
            sendMessage(message, machineUser, machinePassword, basicAuth, orgId, listener);
        }
        return true;
    }

    private void sendMessage(String message, String machineUser, String machinePassword, String basicAuth, String orgId, BuildListener listener) {
        /*
            Messages spark room by executing python script spark_messaging.py, which handles the request calls
        */
        try {
            PythonExecutor pexec = new PythonExecutor(this);
            boolean result = pexec.execPythonBool("message", this.rooms, message, machineUser, machinePassword, orgId, basicAuth);
            if (!result) {
                listener.getLogger().println("Unable to message Spark Room(s)");
                return;
            }
            listener.getLogger().println("Message to Spark Room(s) sent: " + message);
            return;
        } catch (Exception e) {
            listener.getLogger().println("Error Messaging Spark Room: " + e.toString());
        }
        return;
    }

    private String replaceVariables(String message, AbstractBuild build, BuildListener listener) {
        try {
            EnvVars env = build.getEnvironment(listener);
            for (String key : env.keySet()) {
                message = message.replaceAll("\\$\\{?" + key + "\\}?", env.get(key));
            }
        } catch (Exception e) {
            listener.getLogger().println("Unable to replace all environment variables");
            return message;
        }
        try {
            Map<String, String> vars = build.getBuildVariables();
            for (String key : vars.keySet()) {
                message = message.replaceAll("\\$\\{?" + key + "\\}?", vars.get(key));
            }
        } catch (Exception e) {
            listener.getLogger().println("Unable to replace all build parameters");
            return message;
        }
        return message;
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

    public String getStartMessage() {
        return startMessage;
    }
    public String getFailMessage() {
        return failMessage;
    }
    public String getSuccessMessage() {
        return successMessage;
    }

    public boolean getStart() {
        return start;
    }
    public boolean getFail() {
        return fail;
    }
    public boolean getSuccess() {
        return success;
    }

    public boolean getAddUrl() {
        return addUrl;
    }


    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        /*
            Values from Global configuration form
        */
        private String machineUser;
        private String machinePassword;
        private String basicAuth;
        private String orgId;

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
            basicAuth = formData.getString("basicAuth");
            orgId = formData.getString("orgId");
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

        public String getBasicAuth() {
            return basicAuth;
        }

        public void setBasicAuth(String basicAuth) {
            this.basicAuth = basicAuth;
        }

        public String getOrgId() {
            return orgId;
        }

        public void setOrgId(String orgId) {
            this.orgId = orgId;
        }

        public FormValidation doRoomCheck(@QueryParameter String roomString) throws IOException, ServletException {
            if (roomString == null || roomString.trim().length() == 0) {
                return FormValidation.warning("Spark rooms required");
            }
            return FormValidation.ok();
        }

        private boolean isEmpty(String s) {
            return s == null || s.trim().length() == 0;
        }

        /*
            Add machine button implemented as "form validation" so it can be in the build config.
            Requires extra oauthToken field.
        */
        public FormValidation doAddMachine(@QueryParameter("oauthToken") final String oauthToken,
                @QueryParameter("rooms") final String rooms) throws IOException, ServletException {
            try {
                SparkMessaging temp = new SparkMessaging();
                PythonExecutor pexec = new PythonExecutor(temp);
                if( isEmpty(this.machineUser) || isEmpty(this.machinePassword) || isEmpty(this.basicAuth) || isEmpty(this.orgId)) {
                    return FormValidation.error("Machine Credentials required");
                }
                if( isEmpty(oauthToken)){
                    return FormValidation.error("User OAuth2 token required");
                }
                boolean result = pexec.execPythonBool("add_machine", rooms, oauthToken, this.machineUser, 
                                                      this.machinePassword, this.orgId, this.basicAuth);
                if (!result) {
                    return FormValidation.error("Could not add Machine Account to one/more of the Rooms.\n" + 
                        "Machine could already be present, or the room-id/Oauth2 token could be incorrect");
                }
                return FormValidation.ok("Success");
            } catch (Exception e) {
                return FormValidation.error("Could not add Machine to Room");
            }
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }

}

