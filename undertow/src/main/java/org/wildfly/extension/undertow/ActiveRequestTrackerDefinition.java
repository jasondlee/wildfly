package org.wildfly.extension.undertow;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import io.undertow.server.ConnectionStatistics;
import io.undertow.server.handlers.ActiveRequestTrackerHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.DynamicNameMappers;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

public class ActiveRequestTrackerDefinition extends PersistentResourceDefinition {
    static final RuntimeCapability<Void> ACTIVE_REQUEST_TRACKING_CAPABILITY =
            RuntimeCapability.Builder.of(Capabilities.CAPABILITY_ACTIVE_REQUEST_TRACKING, true,
                            ActiveRequestTrackerService.class)
                    .setDynamicNameMapper(DynamicNameMappers.GRAND_PARENT)
                    .build();
    protected static final SimpleAttributeDefinition PREDICATE = new SimpleAttributeDefinitionBuilder(Constants.PREDICATE, ModelType.STRING, true)
            .setAllowExpression(true)
            .setValidator(PredicateValidator.INSTANCE)
            .setRestartAllServices()
            .build();
    private static final SimpleOperationDefinition LIST_ACTIVE_REQUESTS =
            new SimpleOperationDefinitionBuilder("list-active-requests",
                    UndertowExtension.getResolver("active-request-tracker"))
            .setReplyType(ModelType.LONG)
            .setRuntimeOnly()
            .build();

    static final Collection<SimpleAttributeDefinition> ATTRIBUTES = Arrays.asList(PREDICATE);
    static final ActiveRequestTrackerDefinition INSTANCE = new ActiveRequestTrackerDefinition();

    protected ActiveRequestTrackerDefinition() {
        super(new SimpleResourceDefinition.Parameters(UndertowExtension.PATH_ACTIVE_REQUEST_TRACKER,
                UndertowExtension.getResolver(Constants.ACTIVE_REQUEST_TRACKER))
                .setAddHandler(ActiveRequestTrackerAdd.INSTANCE)
                .setRemoveHandler(ActiveRequestTrackerRemove.INSTANCE)
                .setCapabilities(ACTIVE_REQUEST_TRACKING_CAPABILITY)
        );
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return (Collection) ATTRIBUTES;
    }

    @Override
    public void registerOperations(final ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(LIST_ACTIVE_REQUESTS, new ListActiveRequestsOperationHandler());
    }

    private static class ListActiveRequestsOperationHandler implements OperationStepHandler {
        @Override
        public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            PathAddress hostPath = context.getCurrentAddress().getParent();
            PathAddress serverPath = hostPath.getParent();

            ServiceName serviceName = UndertowService.activeRequestTrackingServiceName(serverPath.getLastElement().getValue(),
                    hostPath.getLastElement().getValue());

            ServiceRegistry serviceRegistry = context.getServiceRegistry(false);
            ServiceController<?> controller = serviceRegistry.getService(serviceName);

            //check if deployment is active at all
            if (controller == null || controller.getState() != ServiceController.State.UP) {
                return;
            }

            ActiveRequestTrackerHandler handler = ((ActiveRequestTrackerService) controller.getService()).getHandler();

            if (handler != null) {
                List<ConnectionStatistics> stats = handler.getTrackedRequests();

                if (!stats.isEmpty()) {
                    ModelNode result = new ModelNode();
                    stats.forEach(c -> {
                        ModelNode chanInfo = new ModelNode();
                        chanInfo.get("remote-address").set(c.getRemoteAddress());
                        chanInfo.get("uri").set(c.getUri());
                        chanInfo.get("http-method").set(c.getMethod());
                        chanInfo.get("protocol").set(c.getProtocol());
                        chanInfo.get("query-string").set(c.getQueryString());
                        chanInfo.get("bytes-received").set(c.getBytesReceived());
                        chanInfo.get("bytes-sent").set(c.getBytesSent());
                        chanInfo.get("start-time").set(c.getStartTime());
                        chanInfo.get("processing-time").set(c.getProcessingTime());
                        result.add(chanInfo);
                    });
                    context.getResult().set(result);
                }
            }
        }
    }
}
