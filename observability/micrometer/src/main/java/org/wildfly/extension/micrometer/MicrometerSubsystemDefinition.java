/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.micrometer;

import java.util.Arrays;
import java.util.Collection;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceName;
import org.wildfly.extension.micrometer.metrics.MetricCollector;
import org.wildfly.extension.micrometer.metrics.WildFlyRegistry;

public class MicrometerSubsystemDefinition extends PersistentResourceDefinition {
    public static final String MICROMETER_MODULE = "org.wildfly.extension.micrometer";
    public static final String MICROMETER_API_MODULE = "org.wildfly.extension.micrometer-api";

    public static final String CLIENT_FACTORY_CAPABILITY = "org.wildfly.management.model-controller-client-factory";
    public static final String HTTP_EXTENSIBILITY_CAPABILITY = "org.wildfly.management.http.extensible";
    public static final String MANAGEMENT_EXECUTOR = "org.wildfly.management.executor";
    public static final String METRICS_SCAN_CAPABILITY = "org.wildfly.extension.metrics.scan";
    public static final String MICROMETER_HTTP_SECURITY_CAPABILITY = MICROMETER_MODULE + ".http-context.security-enabled";
    public static final String PROCESS_STATE_NOTIFIER = "org.wildfly.management.process-state-notifier";
    private static final String METRICS_HTTP_CONTEXT_CAPABILITY = "org.wildfly.extension.metrics.http-context";

    private static final RuntimeCapability<Void> MICROMETER_COLLECTOR_RUNTIME_CAPABILITY =
            RuntimeCapability.Builder.of(MICROMETER_MODULE + ".wildfly-collector", MetricCollector.class)
                    .addRequirements(CLIENT_FACTORY_CAPABILITY, MANAGEMENT_EXECUTOR, PROCESS_STATE_NOTIFIER)
                    .build();
    public static final RuntimeCapability<Void> MICROMETER_REGISTRY_RUNTIME_CAPABILITY =
            RuntimeCapability.Builder.of(MICROMETER_MODULE + ".registry", WildFlyRegistry.class)
                    .build();
    static final RuntimeCapability<Void> MICROMETER_HTTP_CONTEXT_CAPABILITY =
            RuntimeCapability.Builder.of(MICROMETER_MODULE + ".http-context", MicrometerContextService.class)
//                    .addRequirements(METRICS_HTTP_CONTEXT_CAPABILITY)
                    .build();

    public static final ServiceName MICROMETER_COLLECTOR = MICROMETER_COLLECTOR_RUNTIME_CAPABILITY.getCapabilityServiceName();

    public static final String[] MODULES = {
    };

    public static final String[] EXPORTED_MODULES = {
            MICROMETER_API_MODULE
    };

    static final AttributeDefinition SECURITY_ENABLED =
            SimpleAttributeDefinitionBuilder.create("security-enabled", ModelType.BOOLEAN)
                    .setDefaultValue(ModelNode.FALSE)
                    .setRequired(false)
                    .setRestartAllServices()
                    .setAllowExpression(true)
                    .build();

    static final StringListAttributeDefinition EXPOSED_SUBSYSTEMS =
            new StringListAttributeDefinition.Builder("exposed-subsystems")
                    .setDefaultValue(new ModelNode().add("*"))
                    .setRequired(false)
                    .setRestartAllServices()
                    .build();

    static final AttributeDefinition PREFIX =
            SimpleAttributeDefinitionBuilder.create("prefix", ModelType.STRING)
                    .setRequired(false)
                    .setRestartAllServices()
                    .setAllowExpression(true)
                    .build();

    public static final AttributeDefinition[] ATTRIBUTES = {SECURITY_ENABLED, EXPOSED_SUBSYSTEMS, PREFIX};
    public static final MicrometerSubsystemDefinition INSTANCE = new MicrometerSubsystemDefinition();

    protected MicrometerSubsystemDefinition() {
        super(new SimpleResourceDefinition.Parameters(MicrometerSubsystemExtension.SUBSYSTEM_PATH,
                MicrometerSubsystemExtension.getResourceDescriptionResolver())
                .setAddHandler(MicrometerSubsystemAdd.INSTANCE)
                .setRemoveHandler(MicrometerSubsystemRemove.INSTANCE)
                .addCapabilities(MICROMETER_HTTP_CONTEXT_CAPABILITY));
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(ATTRIBUTES);
    }
}
