package org.wildfly.extension.undertow;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;

public class ActiveRequestTrackerRemove extends AbstractRemoveStepHandler {
    public static final ActiveRequestTrackerRemove INSTANCE = new ActiveRequestTrackerRemove();

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) {
        final PathAddress hostAddress = context.getCurrentAddress().getParent();
        final ServiceName serviceName = UndertowService.activeRequestTrackingServiceName(
                hostAddress.getParent().getLastElement().getValue(),
                hostAddress.getLastElement().getValue());
        context.removeService(serviceName);
    }
}
