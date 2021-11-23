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

import static org.jboss.as.controller.OperationContext.Stage.RUNTIME;
import static org.jboss.as.controller.OperationContext.Stage.VERIFY;
import static org.jboss.as.controller.PathAddress.EMPTY_ADDRESS;
import static org.jboss.as.server.deployment.Phase.DEPENDENCIES;
import static org.jboss.as.server.deployment.Phase.POST_MODULE;
import static org.jboss.as.server.deployment.Phase.POST_MODULE_METRICS;
import static org.wildfly.extension.micrometer.MicrometerSubsystemDefinition.MICROMETER_COLLECTOR;
import static org.wildfly.extension.micrometer.MicrometerSubsystemExtension.SUBSYSTEM_NAME;

import java.util.List;
import java.util.function.Function;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.wildfly.extension.micrometer.deployment.MicrometerSubsystemDeploymentProcessor;
import org.wildfly.extension.micrometer.metrics.MetricCollector;
import org.wildfly.extension.micrometer.metrics.MetricsCollectorService;

public class MicrometerSubsystemAdd extends AbstractBoottimeAddStepHandler {
    MicrometerSubsystemAdd() {
        super(MicrometerSubsystemDefinition.ATTRIBUTES);
    }

    public static final MicrometerSubsystemAdd INSTANCE = new MicrometerSubsystemAdd();

    /**
     * {@inheritDoc}
     */
    @Override
    protected void performBoottime(OperationContext context, ModelNode operation, ModelNode model)
            throws OperationFailedException {
        super.performBoottime(context, operation, model);

        List<String> exposedSubsystems = MicrometerSubsystemDefinition.EXPOSED_SUBSYSTEMS.unwrap(context, model);
        boolean exposeAnySubsystem = exposedSubsystems.remove("*");
        String prefix = MicrometerSubsystemDefinition.PREFIX.resolveModelAttribute(context, model)
                .asStringOrNull();
        boolean securityEnabled = MicrometerSubsystemDefinition.SECURITY_ENABLED.resolveModelAttribute(context, model)
                .asBoolean();

        MicrometerRegistryService.install(context, securityEnabled);
        MetricsCollectorService.install(context);
        MicrometerContextService.install(context, securityEnabled);

        context.addStep(new AbstractDeploymentChainStep() {
            @Override
            public void execute(DeploymentProcessorTarget processorTarget) {
                processorTarget.addDeploymentProcessor(SUBSYSTEM_NAME, DEPENDENCIES, 0x1910,
                        new MicrometerDependencyProcessor());
                processorTarget.addDeploymentProcessor(SUBSYSTEM_NAME, POST_MODULE, POST_MODULE_METRICS-1, // ???
                        new MicrometerSubsystemDeploymentProcessor(exposeAnySubsystem, exposedSubsystems, prefix));
            }
        }, RUNTIME);

        context.addStep((operationContext, modelNode) -> {
            ServiceController<?> serviceController = context.getServiceRegistry(false).getService(MICROMETER_COLLECTOR);
            MetricCollector metricCollector = MetricCollector.class.cast(serviceController.getValue());

            ImmutableManagementResourceRegistration rootResourceRegistration = context.getRootResourceRegistration();
            Resource rootResource = context.readResourceFromRoot(EMPTY_ADDRESS);

            metricCollector.collectResourceMetrics(rootResource, rootResourceRegistration,
                    Function.identity(), exposeAnySubsystem, exposedSubsystems, prefix, null);
        }, VERIFY);

        MicrometerExtensionLogger.MICROMETER_LOGGER.activatingSubsystem();
    }
}
