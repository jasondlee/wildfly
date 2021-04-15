package org.wildfly.extension.opentelemetry.extension;

import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.extension.opentelemetry.OpenTelemetryConfigurationConstants;

public class OpenTelemetrySubsystemDefinition extends SimpleResourceDefinition {
    public static final OpenTelemetrySubsystemDefinition INSTANCE = new OpenTelemetrySubsystemDefinition();
    static final String OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME = "org.wildfly.network.outbound-socket-binding";

    /*
    static final AttributeDefinition SECURITY_ENABLED = SimpleAttributeDefinitionBuilder.create("security-enabled", ModelType.BOOLEAN)
            .setDefaultValue(ModelNode.TRUE)
            .setRequired(false)
            .setRestartAllServices()
            .setAllowExpression(true)
            .build();

    static final StringListAttributeDefinition EXPOSED_SUBSYSTEMS = new StringListAttributeDefinition.Builder("exposed-subsystems")
            .setRequired(false)
            .setRestartAllServices()
            .build();

    static final AttributeDefinition PREFIX = SimpleAttributeDefinitionBuilder.create("prefix", ModelType.STRING)
            .setRequired(false)
            .setRestartAllServices()
            .setAllowExpression(true)
            .build();
     */

    public static final StringListAttributeDefinition PROPAGATION = StringListAttributeDefinition.Builder.of(OpenTelemetryConfigurationConstants.PROPAGATION)
            .setAllowNullElement(false)
            .setRequired(false)
            .setAllowedValues("JAEGER", "B3")
            .setAttributeGroup("codec-configuration")
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition SAMPLER_TYPE = SimpleAttributeDefinitionBuilder.create(OpenTelemetryConfigurationConstants.SAMPLER_TYPE, ModelType.STRING, true)
            .setAllowedValues("const", "probabilistic", "ratelimiting", "remote")
            .setDefaultValue(new ModelNode("remote"))
            .setAttributeGroup("sampler-configuration")
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();
    public static final SimpleAttributeDefinition SAMPLER_PARAM = SimpleAttributeDefinitionBuilder.create(OpenTelemetryConfigurationConstants.SAMPLER_PARAM, ModelType.DOUBLE, true)
            .setAttributeGroup("sampler-configuration")
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();
    public static final SimpleAttributeDefinition SAMPLER_MANAGER_HOST_PORT = SimpleAttributeDefinitionBuilder.create(OpenTelemetryConfigurationConstants.SAMPLER_MANAGER_HOST_PORT, ModelType.STRING, true)
            .setAttributeGroup("sampler-configuration")
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition SENDER_BINDING = SimpleAttributeDefinitionBuilder.create(OpenTelemetryConfigurationConstants.SENDER_AGENT_BINDING, ModelType.STRING, true)
            .setAttributeGroup("sender-configuration")
            .setAllowExpression(true)
            .setCapabilityReference(OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_BINDING_REF)
            .setRestartAllServices()
            .build();
    public static final SimpleAttributeDefinition SENDER_ENDPOINT = SimpleAttributeDefinitionBuilder.create(OpenTelemetryConfigurationConstants.SENDER_ENDPOINT, ModelType.STRING, true)
            .setAttributeGroup("sender-configuration")
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();
    public static final SimpleAttributeDefinition SENDER_AUTH_TOKEN = SimpleAttributeDefinitionBuilder.create(OpenTelemetryConfigurationConstants.SENDER_AUTH_TOKEN, ModelType.STRING, true)
            .setAttributeGroup("sender-configuration")
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();
    public static final SimpleAttributeDefinition SENDER_AUTH_USER = SimpleAttributeDefinitionBuilder.create(OpenTelemetryConfigurationConstants.SENDER_AUTH_USER, ModelType.STRING, true)
            .setAttributeGroup("sender-configuration")
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();
    public static final SimpleAttributeDefinition SENDER_AUTH_PASSWORD = SimpleAttributeDefinitionBuilder.create(OpenTelemetryConfigurationConstants.SENDER_AUTH_PASSWORD, ModelType.STRING, true)
            .setAttributeGroup("sender-configuration")
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition REPORTER_LOG_SPANS = SimpleAttributeDefinitionBuilder.create(OpenTelemetryConfigurationConstants.REPORTER_LOG_SPANS, ModelType.BOOLEAN, true)
            .setAttributeGroup("reporter-configuration")
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();
    public static final SimpleAttributeDefinition REPORTER_FLUSH_INTERVAL = SimpleAttributeDefinitionBuilder.create(OpenTelemetryConfigurationConstants.REPORTER_FLUSH_INTERVAL, ModelType.INT, true)
            .setAttributeGroup("reporter-configuration")
            .setAllowExpression(true)
            .setMeasurementUnit(MeasurementUnit.MILLISECONDS)
            .setRestartAllServices()
            .build();
    public static final SimpleAttributeDefinition REPORTER_MAX_QUEUE_SIZE = SimpleAttributeDefinitionBuilder.create(OpenTelemetryConfigurationConstants.REPORTER_MAX_QUEUE_SIZE, ModelType.INT, true)
            .setAttributeGroup("reporter-configuration")
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition TRACEID_128BIT = SimpleAttributeDefinitionBuilder.create(OpenTelemetryConfigurationConstants.TRACEID_128BIT, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.FALSE)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final PropertiesAttributeDefinition TRACER_TAGS = new PropertiesAttributeDefinition.Builder(OpenTelemetryConfigurationConstants.TRACER_TAGS, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    private OpenTelemetrySubsystemDefinition() {
        super(OpenTelemetrySubsystemExtension.SUBSYSTEM_PATH,
                OpenTelemetrySubsystemExtension.getResourceDescriptionResolver(OpenTelemetrySubsystemExtension.SUBSYSTEM_NAME),
                //We always need to add an 'add' operation
                OpenTelemetrySubsystemAdd.INSTANCE,
                //Every resource that is added, normally needs a remove operation
                OpenTelemetrySubsystemRemove.INSTANCE);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        //you can register aditional operations here
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        //you can register attributes here
    }
}
