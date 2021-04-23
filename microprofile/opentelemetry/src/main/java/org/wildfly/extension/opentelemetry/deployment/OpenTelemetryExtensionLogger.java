package org.wildfly.extension.opentelemetry.deployment;

import static org.jboss.logging.Logger.Level.DEBUG;
import static org.jboss.logging.Logger.Level.INFO;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;

public interface OpenTelemetryExtensionLogger extends BasicLogger {
    OpenTelemetryExtensionLogger ROOT_LOGGER = Logger.getMessageLogger(OpenTelemetryExtensionLogger.class,
            OpenTelemetryExtensionLogger.class.getPackage().getName());

    @LogMessage(level = INFO)
    @Message(id = 1, value = "Activating OpenTelemetry Subsystem")
    void activatingSubsystem();

    @LogMessage(level = DEBUG)
    @Message(id = 2, value = "OpenTelemetry Subsystem is processing deployment")
    void processingDeployment();

    @LogMessage(level = DEBUG)
    @Message(id = 3, value = "The deployment does not have Jakarta Contexts and Dependency Injection enabled. Skipping OpenTelemetry integration.")
    void noCdiDeployment();

    @Message(id = 4, value = "Deployment %s requires use of the '%s' capability but it is not currently registered")
    String deploymentRequiresCapability(String deploymentName, String capabilityName);

}
