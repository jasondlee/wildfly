package org.wildfly.extension.opentelemetry.extension;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLParser;

public class TelemetryParser_1_0 extends PersistentResourceXMLParser {
    /**
     * The name space used for the {@code subsystem} element
     */
    public static final String NAMESPACE = "urn:wildfly:telemetry:1.0";

    private static final PersistentResourceXMLDescription xmlDescription;

    static {
        xmlDescription = builder(TelemetrySubsystemExtension.SUBSYSTEM_PATH, NAMESPACE)
                .addAttributes(
                        TelemetrySubsystemDefinition.SECURITY_ENABLED,
                        TelemetrySubsystemDefinition.EXPOSED_SUBSYSTEMS,
                        TelemetrySubsystemDefinition.PREFIX)
                .build();
    }

    @Override
    public PersistentResourceXMLDescription getParserDescription() {
        return xmlDescription;
    }
}
