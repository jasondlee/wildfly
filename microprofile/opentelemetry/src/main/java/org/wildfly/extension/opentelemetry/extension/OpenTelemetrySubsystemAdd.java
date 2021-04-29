package org.wildfly.extension.opentelemetry.extension;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.opentelemetry.deployment.OpenTelemetryDependencyProcessor;
import org.wildfly.extension.opentelemetry.deployment.OpenTelemetrySubsystemDeploymentProcessor;

/**
 * Handler responsible for adding the subsystem resource to the model
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class OpenTelemetrySubsystemAdd extends AbstractBoottimeAddStepHandler {

    static final OpenTelemetrySubsystemAdd INSTANCE = new OpenTelemetrySubsystemAdd();

    private OpenTelemetrySubsystemAdd() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void performBoottime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        super.performBoottime(context, operation, model);

//        List<String> exposedSubsystems = TelemetrySubsystemDefinition.EXPOSED_SUBSYSTEMS.unwrap(context, model);
//        boolean exposeAnySubsystem = exposedSubsystems.remove("*");
//        String prefix = TelemetrySubsystemDefinition.PREFIX.resolveModelAttribute(context, model).asStringOrNull();
//        boolean securityEnabled = TelemetrySubsystemDefinition.SECURITY_ENABLED.resolveModelAttribute(context, model).asBoolean();


        context.addStep(new AbstractDeploymentChainStep() {
            public void execute(DeploymentProcessorTarget processorTarget) {
                processorTarget.addDeploymentProcessor(
                        OpenTelemetrySubsystemExtension.SUBSYSTEM_NAME,
                        Phase.DEPENDENCIES,
                        0x1910,
                        new OpenTelemetryDependencyProcessor()
                );
                processorTarget.addDeploymentProcessor(
                        OpenTelemetrySubsystemExtension.SUBSYSTEM_NAME,
                        Phase.POST_MODULE,
                        0x3810,
                        new OpenTelemetrySubsystemDeploymentProcessor());

            }
        }, OperationContext.Stage.RUNTIME);

    }
}
