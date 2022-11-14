package org.wildfly.extension.microprofile.telemetry;

import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.SimpleResourceDefinition;

public class MicroProfileTelemetrySubsystemDefinition extends SimpleResourceDefinition {

    protected MicroProfileTelemetrySubsystemDefinition() {
        super(new SimpleResourceDefinition.Parameters(MicroProfileTelemetryExtension.SUBSYSTEM_PATH,
                MicroProfileTelemetryExtension.getResourceDescriptionResolver(true,
                        MicroProfileTelemetryExtension.SUBSYSTEM_NAME))
                .setAddHandler(MicroProfileTelemetrySubsystemAdd.INSTANCE)
                .setRemoveHandler(new ReloadRequiredRemoveStepHandler())
//                .setCapabilities(MICROPROFILE_METRIC_HTTP_CONTEXT_CAPABILITY,
//                        MICROPROFILE_METRICS_HTTP_SECURITY_CAPABILITY,
//                        MICROPROFILE_METRICS_SCAN)
        );
    }
}
