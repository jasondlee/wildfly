/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.opentelemetry.api;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.ProcessProducer;
import jakarta.enterprise.inject.spi.Producer;
import jakarta.inject.Singleton;

import io.opentelemetry.api.OpenTelemetry;
import io.smallrye.opentelemetry.api.OpenTelemetryConfig;
import io.smallrye.opentelemetry.implementation.rest.OpenTelemetryClientFilter;
import io.smallrye.opentelemetry.implementation.rest.OpenTelemetryServerFilter;

public final class OpenTelemetryCdiExtension implements Extension {
    private final boolean useServerConfig;
    private final WildFlyOpenTelemetryConfig config;

    public OpenTelemetryCdiExtension(boolean useServerConfig, Map<String, String> config) {
        this (useServerConfig, new WildFlyOpenTelemetryConfig(config));
    }

    public OpenTelemetryCdiExtension(boolean useServerConfig, WildFlyOpenTelemetryConfig config) {
        this.useServerConfig = useServerConfig;
        this.config = config;
    }

    public void beforeBeanDiscovery(@Observes BeforeBeanDiscovery beforeBeanDiscovery, final BeanManager beanManager) {
        beforeBeanDiscovery.addAnnotatedType(beanManager.createAnnotatedType(OpenTelemetryServerFilter.class),
                OpenTelemetryServerFilter.class.getName());
        beforeBeanDiscovery.addAnnotatedType(beanManager.createAnnotatedType(OpenTelemetryClientFilter.class),
                OpenTelemetryClientFilter.class.getName());
    }

    public void processProducers(@Observes ProcessProducer<?, OpenTelemetry> event, BeanManager bm) {
        event.setProducer(new Producer<>() {
            public void dispose(OpenTelemetry instance) {
            }

            @Override
            public Set<InjectionPoint> getInjectionPoints() {
                return new HashSet<>();
            }

            public OpenTelemetry produce(CreationalContext<OpenTelemetry> ctx) {
                OpenTelemetryConfig appConfig = config;
                Set<Bean<?>> beans = bm.getBeans(OpenTelemetryConfig.class);
                if (!beans.isEmpty()) {
                    Bean<?> bean = bm.resolve(beans);
                    appConfig = (OpenTelemetryConfig) bm.getReference(bean, OpenTelemetryConfig.class, ctx);
                }

                return new WildFlyOpenTelemetryProducer(appConfig).getOpenTelemetry();
            }
        });
    }

    public void registerOpenTelemetryBeans(@Observes AfterBeanDiscovery abd) {
        if (useServerConfig) {
            abd.addBean()
                    .scope(Singleton.class)
                    .addQualifier(Default.Literal.INSTANCE)
                    .types(OpenTelemetryConfig.class)
                    .createWith(e -> config);
        }
    }
}
