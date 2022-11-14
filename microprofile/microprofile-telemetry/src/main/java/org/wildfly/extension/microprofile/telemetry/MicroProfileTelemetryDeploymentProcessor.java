package org.wildfly.extension.microprofile.telemetry;

import static org.jboss.as.weld.Capabilities.WELD_CAPABILITY_NAME;
import static org.wildfly.extension.microprofile.telemetry.MicroProfileTelemetryExtensionLogger.MPTEL_LOGGER;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.ee.structure.DeploymentTypeMarker;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.weld.WeldCapability;

public class MicroProfileTelemetryDeploymentProcessor implements DeploymentUnitProcessor {
    @Override
    public void deploy(DeploymentPhaseContext deploymentPhaseContext) throws DeploymentUnitProcessingException {
        MPTEL_LOGGER.activatingSubsystem();

        final DeploymentUnit deploymentUnit = deploymentPhaseContext.getDeploymentUnit();
        if (DeploymentTypeMarker.isType(DeploymentType.EAR, deploymentUnit)) {
            return;
        }

        try {
            final WeldCapability weldCapability = deploymentUnit.getAttachment(Attachments.CAPABILITY_SERVICE_SUPPORT)
                    .getCapabilityRuntimeAPI(WELD_CAPABILITY_NAME, WeldCapability.class);
            if (!weldCapability.isPartOfWeldDeployment(deploymentUnit)) {
                MPTEL_LOGGER.debug("The deployment does not have Jakarta Contexts and Dependency Injection enabled. Skipping MicroProfile Telemetry integration.");
                return;
            }
//            weldCapability.registerExtensionInstance(new OpenTelemetryCdiExtension(config), deploymentUnit);
//            weldCapability.registerExtensionInstance(new OpenTelemetryExtension(), deploymentUnit);
        } catch (CapabilityServiceSupport.NoSuchCapabilityException e) {
            throw MPTEL_LOGGER.deploymentRequiresCapability(deploymentPhaseContext.getDeploymentUnit().getName(),
                    WELD_CAPABILITY_NAME);
        }
    }

    @Override
    public void undeploy(DeploymentUnit context) {
    }
}
