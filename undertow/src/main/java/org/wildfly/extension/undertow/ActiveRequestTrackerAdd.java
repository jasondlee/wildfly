package org.wildfly.extension.undertow;

import java.util.function.Consumer;
import java.util.function.Supplier;

import io.undertow.predicate.Predicate;
import io.undertow.predicate.Predicates;
import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;

public class ActiveRequestTrackerAdd extends AbstractAddStepHandler {
    private ActiveRequestTrackerAdd() {
        super(ActiveRequestTrackerDefinition.ATTRIBUTES);
    }

    static final ActiveRequestTrackerAdd INSTANCE = new ActiveRequestTrackerAdd();

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final PathAddress hostAddress = context.getCurrentAddress().getParent();
        final PathAddress serverAddress = hostAddress.getParent();

        Predicate predicate = null;
        ModelNode predicateNode = ActiveRequestTrackerDefinition.PREDICATE.resolveModelAttribute(context, model);
        if(predicateNode.isDefined()) {
            predicate = Predicates.parse(predicateNode.asString(), getClass().getClassLoader());
        }

        final String serverName = serverAddress.getLastElement().getValue();
        final String hostName = hostAddress.getLastElement().getValue();

        final CapabilityServiceBuilder<?> sb = context.getCapabilityServiceTarget()
                .addCapability(ActiveRequestTrackerDefinition.ACTIVE_REQUEST_TRACKING_CAPABILITY);
        final Consumer<ActiveRequestTrackerService> serviceConsumer = sb.provides(ActiveRequestTrackerDefinition.ACTIVE_REQUEST_TRACKING_CAPABILITY,
                UndertowService.activeRequestTrackingServiceName(serverName, hostName));
        final Supplier<Host> hostSupplier = sb.requiresCapability(Capabilities.CAPABILITY_HOST, Host.class, serverName, hostName);

        ActiveRequestTrackerService service = new ActiveRequestTrackerService(serviceConsumer, hostSupplier, predicate);
        sb.setInstance(service);
        sb.install();
    }
}
