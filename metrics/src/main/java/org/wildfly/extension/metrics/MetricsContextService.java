/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.metrics;

import static org.wildfly.extension.metrics.MetricsSubsystemDefinition.HTTP_EXTENSIBILITY_CAPABILITY;
import static org.wildfly.extension.metrics.MetricsSubsystemDefinition.METRICS_HTTP_CONTEXT_CAPABILITY;
import static org.wildfly.extension.metrics.MetricsSubsystemDefinition.METRICS_HTTP_SECURITY_CAPABILITY;
import static org.wildfly.extension.metrics.MetricsSubsystemDefinition.METRICS_REGISTRY_RUNTIME_CAPABILITY;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.jboss.as.controller.LocalModelControllerClient;
import org.jboss.as.controller.ModelControllerClientFactory;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.RequirementServiceBuilder;
import org.jboss.as.controller.management.Capabilities;
import org.jboss.as.server.mgmt.domain.ExtensibleHttpManagement;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2018 Red Hat inc.
 */
public class MetricsContextService implements Service {

    static final String CONTEXT_NAME = "/metrics";

    private final Consumer<MetricsContextService> consumer;
    private final Supplier<ExtensibleHttpManagement> extensibleHttpManagement;
    private final Supplier<Executor> managementExecutor;
    private Supplier<WildFlyMetricRegistry> wildflyMetricRegistry;
    private final Supplier<ModelControllerClientFactory> modelControllerClientFactory;
    private final Supplier<Boolean> securityEnabledSupplier;
    private final PrometheusExporter prometheusExporter = new PrometheusExporter();
    private HttpHandler overrideableMetricHandler;

    static void install(OperationContext context, boolean securityEnabled) {
        RequirementServiceBuilder<?> serviceBuilder = context.getCapabilityServiceTarget().addService(METRICS_HTTP_CONTEXT_CAPABILITY.getCapabilityServiceName());
        Supplier<ExtensibleHttpManagement> extensibleHttpManagement = serviceBuilder.requires(context.getCapabilityServiceName(HTTP_EXTENSIBILITY_CAPABILITY, ExtensibleHttpManagement.class));
        Supplier<WildFlyMetricRegistry> wildflyMetricRegistry = serviceBuilder.requires(METRICS_REGISTRY_RUNTIME_CAPABILITY.getCapabilityServiceName());
        Supplier<ModelControllerClientFactory> modelControllerClientFactory = serviceBuilder.requires(ModelControllerClientFactory.SERVICE_DESCRIPTOR);
        Supplier<Executor> managementExecutor = serviceBuilder.requires(Capabilities.MANAGEMENT_EXECUTOR);
        Consumer<MetricsContextService> metricsContext = serviceBuilder.provides(METRICS_HTTP_CONTEXT_CAPABILITY.getCapabilityServiceName());
        final Supplier<Boolean> securityEnabledSupplier;
        if (context.getCapabilityServiceSupport().hasCapability(METRICS_HTTP_SECURITY_CAPABILITY)) {
            securityEnabledSupplier = serviceBuilder.requires(ServiceName.parse(METRICS_HTTP_SECURITY_CAPABILITY));
        } else {
            securityEnabledSupplier = new Supplier<Boolean>() {
                @Override
                public Boolean get() {
                    return securityEnabled;
                }
            };
        }
        Service metricsContextService =
                new MetricsContextService(metricsContext, extensibleHttpManagement,
                                          wildflyMetricRegistry,
                                          modelControllerClientFactory,
                                          managementExecutor,
                                          securityEnabledSupplier);

        serviceBuilder.setInstance(metricsContextService)
                .install();
    }

    public MetricsContextService(Consumer<MetricsContextService> consumer,
            Supplier<ExtensibleHttpManagement> extensibleHttpManagement,
            Supplier<WildFlyMetricRegistry> wildflyMetricRegistry,
            Supplier<ModelControllerClientFactory> modelControllerClientFactory,
            Supplier<Executor> managementExecutor,
            Supplier<Boolean> securityEnabledSupplier) {
        this.consumer = consumer;
        this.extensibleHttpManagement = extensibleHttpManagement;
        this.wildflyMetricRegistry = wildflyMetricRegistry;
        this.modelControllerClientFactory = modelControllerClientFactory;
        this.managementExecutor = managementExecutor;
        this.securityEnabledSupplier = securityEnabledSupplier;
    }

    @Override
    public void start(StartContext context) {
        extensibleHttpManagement.get().addManagementHandler(CONTEXT_NAME,
                securityEnabledSupplier.get(), new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                if (overrideableMetricHandler != null) {
                    overrideableMetricHandler.handleRequest(exchange);
                    return;
                }

                WildFlyMetricRegistry metricRegistry = wildflyMetricRegistry.get();
                metricRegistry.readLock();
                LocalModelControllerClient client = modelControllerClientFactory.get()
                    .createClient(managementExecutor.get());
                try {
                    String wildFlyMetrics = prometheusExporter.export(client, metricRegistry);
                    exchange.getResponseSender().send(wildFlyMetrics);
                } finally {
                    client.close();
                    metricRegistry.unlock();
                }
            }
        });
        consumer.accept(this);
    }

    @Override
    public void stop(StopContext context) {
        extensibleHttpManagement.get().removeContext(CONTEXT_NAME);
        consumer.accept(null);
    }

    public void setOverrideableMetricHandler(HttpHandler handler) {
        this.overrideableMetricHandler = handler;
    }
}
