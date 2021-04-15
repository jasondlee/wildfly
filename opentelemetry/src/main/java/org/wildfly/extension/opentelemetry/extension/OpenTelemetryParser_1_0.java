package org.wildfly.extension.opentelemetry.extension;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;
import static org.wildfly.extension.opentelemetry.extension.OpenTelemetrySubsystemDefinition.PROPAGATION;
import static org.wildfly.extension.opentelemetry.extension.OpenTelemetrySubsystemDefinition.REPORTER_FLUSH_INTERVAL;
import static org.wildfly.extension.opentelemetry.extension.OpenTelemetrySubsystemDefinition.REPORTER_LOG_SPANS;
import static org.wildfly.extension.opentelemetry.extension.OpenTelemetrySubsystemDefinition.REPORTER_MAX_QUEUE_SIZE;
import static org.wildfly.extension.opentelemetry.extension.OpenTelemetrySubsystemDefinition.SAMPLER_MANAGER_HOST_PORT;
import static org.wildfly.extension.opentelemetry.extension.OpenTelemetrySubsystemDefinition.SAMPLER_PARAM;
import static org.wildfly.extension.opentelemetry.extension.OpenTelemetrySubsystemDefinition.SAMPLER_TYPE;
import static org.wildfly.extension.opentelemetry.extension.OpenTelemetrySubsystemDefinition.SENDER_AUTH_PASSWORD;
import static org.wildfly.extension.opentelemetry.extension.OpenTelemetrySubsystemDefinition.SENDER_AUTH_TOKEN;
import static org.wildfly.extension.opentelemetry.extension.OpenTelemetrySubsystemDefinition.SENDER_AUTH_USER;
import static org.wildfly.extension.opentelemetry.extension.OpenTelemetrySubsystemDefinition.SENDER_BINDING;
import static org.wildfly.extension.opentelemetry.extension.OpenTelemetrySubsystemDefinition.SENDER_ENDPOINT;
import static org.wildfly.extension.opentelemetry.extension.OpenTelemetrySubsystemDefinition.TRACEID_128BIT;
import static org.wildfly.extension.opentelemetry.extension.OpenTelemetrySubsystemDefinition.TRACER_TAGS;

import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLParser;

public class OpenTelemetryParser_1_0 extends PersistentResourceXMLParser {
    /**
     * The name space used for the {@code subsystem} element
     */
    public static final String NAMESPACE = "urn:wildfly:opentelemetry:1.0";

    private static final PersistentResourceXMLDescription xmlDescription;

    static {
        xmlDescription = builder(OpenTelemetrySubsystemExtension.SUBSYSTEM_PATH, NAMESPACE)
                .addAttributes(
                        PROPAGATION, SAMPLER_TYPE, SAMPLER_PARAM, SAMPLER_MANAGER_HOST_PORT,
                        SENDER_BINDING, SENDER_ENDPOINT, SENDER_AUTH_TOKEN,
                        SENDER_AUTH_USER, SENDER_AUTH_PASSWORD, REPORTER_LOG_SPANS,
                        REPORTER_FLUSH_INTERVAL, REPORTER_MAX_QUEUE_SIZE, TRACER_TAGS, TRACEID_128BIT
                )
                .build();
    }

    @Override
    public PersistentResourceXMLDescription getParserDescription() {
        return xmlDescription;
    }
}
