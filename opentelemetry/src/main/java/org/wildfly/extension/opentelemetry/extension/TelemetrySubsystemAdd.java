package org.wildfly.extension.opentelemetry.extension;

import java.util.List;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.opentelemetry.deployment.TelemetrySubsystemDeploymentProcessor;

/**
 * Handler responsible for adding the subsystem resource to the model
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class TelemetrySubsystemAdd extends AbstractBoottimeAddStepHandler {

    static final TelemetrySubsystemAdd INSTANCE = new TelemetrySubsystemAdd();

    private TelemetrySubsystemAdd() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void performBoottime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        super.performBoottime(context, operation, model);

        List<String> exposedSubsystems = TelemetrySubsystemDefinition.EXPOSED_SUBSYSTEMS.unwrap(context, model);
        boolean exposeAnySubsystem = exposedSubsystems.remove("*");
        String prefix = TelemetrySubsystemDefinition.PREFIX.resolveModelAttribute(context, model).asStringOrNull();
        boolean securityEnabled = TelemetrySubsystemDefinition.SECURITY_ENABLED.resolveModelAttribute(context, model).asBoolean();

        context.addStep(new AbstractDeploymentChainStep() {
            public void execute(DeploymentProcessorTarget processorTarget) {
                processorTarget.addDeploymentProcessor(TelemetrySubsystemExtension.SUBSYSTEM_NAME,
                        TelemetrySubsystemDeploymentProcessor.PHASE,
                        TelemetrySubsystemDeploymentProcessor.PRIORITY,
                        new TelemetrySubsystemDeploymentProcessor());

            }
        }, OperationContext.Stage.RUNTIME);

    }
}
