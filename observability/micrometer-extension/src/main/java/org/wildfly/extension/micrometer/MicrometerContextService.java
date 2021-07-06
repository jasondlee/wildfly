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

import static org.wildfly.extension.micrometer.MicrometerSubsystemDefinition.HTTP_EXTENSIBILITY_CAPABILITY;
import static org.wildfly.extension.micrometer.MicrometerSubsystemDefinition.MICROMETER_HTTP_CONTEXT_CAPABILITY;
import static org.wildfly.extension.micrometer.MicrometerSubsystemDefinition.MICROMETER_HTTP_SECURITY_CAPABILITY;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.server.mgmt.domain.ExtensibleHttpManagement;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;

public class MicrometerContextService implements Service {
    private static final String CONTEXT_NAME = "/micrometer";

    private final Consumer<MicrometerContextService> consumer;
    private final Supplier<ExtensibleHttpManagement> extensibleHttpManagement;
    private final Map<REGISTRY_TYPES, MeterRegistry> registries = new HashMap<>();
    private final Supplier<Boolean> securityEnabledSupplier;

    // eh?
    private enum REGISTRY_TYPES {
        APP, JVM, VENDOR
    }

    ;

    static MicrometerContextService install(OperationContext context, boolean securityEnabled) {
        ServiceBuilder<?> serviceBuilder = context.getServiceTarget()
                .addService(MICROMETER_HTTP_CONTEXT_CAPABILITY.getCapabilityServiceName());

        Supplier<ExtensibleHttpManagement> extensibleHttpManagement =
                serviceBuilder.requires(context.getCapabilityServiceName(HTTP_EXTENSIBILITY_CAPABILITY,
                        ExtensibleHttpManagement.class));
        Consumer<MicrometerContextService> metricsContext =
                serviceBuilder.provides(MICROMETER_HTTP_CONTEXT_CAPABILITY.getCapabilityServiceName());
        final Supplier<Boolean> securityEnabledSupplier;
        if (context.getCapabilityServiceSupport().hasCapability(MICROMETER_HTTP_SECURITY_CAPABILITY)) {
            securityEnabledSupplier = serviceBuilder.requires(ServiceName.parse(MICROMETER_HTTP_SECURITY_CAPABILITY));
        } else {
            securityEnabledSupplier = () -> securityEnabled;
        }

        // ???
        MicrometerContextService service = new MicrometerContextService(metricsContext,
                extensibleHttpManagement,
                securityEnabledSupplier);

        serviceBuilder.setInstance(service)
                .install();

        return service;
    }

    private MicrometerContextService(Consumer<MicrometerContextService> consumer,
                                     Supplier<ExtensibleHttpManagement> extensibleHttpManagement,
                                     Supplier<Boolean> securityEnabledSupplier) {
        this.consumer = consumer;
        this.extensibleHttpManagement = extensibleHttpManagement;
        this.securityEnabledSupplier = securityEnabledSupplier;

        registries.put(REGISTRY_TYPES.JVM,
                ((Supplier<MeterRegistry>) () -> {
                    final PrometheusMeterRegistry jvmRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
                    new ClassLoaderMetrics().bindTo(jvmRegistry);
                    new JvmMemoryMetrics().bindTo(jvmRegistry);
                    new JvmGcMetrics().bindTo(jvmRegistry);
                    new ProcessorMetrics().bindTo(jvmRegistry);
                    new JvmThreadMetrics().bindTo(jvmRegistry);
                    return jvmRegistry;
                })
                        .get());
        registries.put(REGISTRY_TYPES.VENDOR, new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
        registries.put(REGISTRY_TYPES.APP, new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
    }

    @Override
    public void start(StartContext context) {
        extensibleHttpManagement.get().addManagementHandler(CONTEXT_NAME, securityEnabledSupplier.get(), new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) {
                StringBuilder sb = new StringBuilder();
                registries.values().forEach(r -> {
                    if (r instanceof PrometheusMeterRegistry) {
                        sb.append(((PrometheusMeterRegistry) r).scrape());
                    }
                });
                exchange.getResponseSender().send(sb.toString());
            }
        });
        consumer.accept(this);
    }

    @Override
    public void stop(StopContext context) {
        extensibleHttpManagement.get().removeContext(CONTEXT_NAME);
        consumer.accept(null);
    }

    public MeterRegistry getApplicationMetricsRegistry() {
        return registries.get(REGISTRY_TYPES.APP);
    }
}
