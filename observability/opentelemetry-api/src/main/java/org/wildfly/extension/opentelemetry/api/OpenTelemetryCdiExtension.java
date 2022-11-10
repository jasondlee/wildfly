package org.wildfly.extension.opentelemetry.api;

import io.smallrye.opentelemetry.api.OpenTelemetryConfig;
import io.smallrye.opentelemetry.implementation.rest.OpenTelemetryClientFilter;
import io.smallrye.opentelemetry.implementation.rest.OpenTelemetryServerFilter;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.inject.Singleton;

public class OpenTelemetryCdiExtension implements Extension {
    private final OpenTelemetryConfig config;

    public OpenTelemetryCdiExtension(OpenTelemetryConfig config) {
        this.config = config;
    }

    public void beforeBeanDiscovery(@Observes BeforeBeanDiscovery beforeBeanDiscovery, final BeanManager beanManager) {
        beforeBeanDiscovery.addAnnotatedType(beanManager.createAnnotatedType(OpenTelemetryServerFilter.class),
                OpenTelemetryServerFilter.class.getName());
        beforeBeanDiscovery.addAnnotatedType(beanManager.createAnnotatedType(OpenTelemetryClientFilter.class),
                OpenTelemetryClientFilter.class.getName());
    }

    public void registerOpenTelemetryConfigBean(@Observes AfterBeanDiscovery abd) {
        try {
            // If this class is found, then we don't need to inject our server-config-based configuration, as it's
            // provided by the smallrye-opentelemetry-config module and MP Config
            // TODO: Use a capability check for this
            Class.forName("io.smallrye.opentelemetry.implementation.config.OpenTelemetryConfigProducer");
        } catch (ClassNotFoundException cnfe) {
            abd.addBean()
                    .scope(Singleton.class)
                    .addQualifier(Default.Literal.INSTANCE)
                    .types(OpenTelemetryConfig.class)
                    .createWith(e -> config);
        }
    }
}
