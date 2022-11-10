package org.wildfly.extension.opentelemetry.api;

import io.smallrye.opentelemetry.api.OpenTelemetryConfig;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.inject.Singleton;

public class OpenTelemetryCdiExtension implements Extension {
    private final OpenTelemetryConfig config;

    public OpenTelemetryCdiExtension(OpenTelemetryConfig config) {
        this.config = config;
    }

    public void registerOtelConfigBean(@Observes AfterBeanDiscovery abd, BeanManager beanManager) {
        try {
            // If this class is found, then we don't need to inject our server-config-based configuration, as it's
            // provided by the smallrye-opentelemetr-config module and MP Config
            // TODO: Use a capability check for this
            Class.forName("io.smallrye.opentelemetry.implementation.config.OpenTelemetryConfigProducer");
        } catch (ClassNotFoundException cnfe) {

            System.out.println("Adding bean: " + config.getClass().getCanonicalName());
            abd.addBean()
                    .scope(Singleton.class)
                    .addQualifier(Default.Literal.INSTANCE)
                    .types(OpenTelemetryConfig.class)
//                    .addTransitiveTypeClosure(OpenTelemetryConfig.class)
                    .createWith(e -> {
                        System.out.println("'Creating' config");
                        return config;
                    });
        }
    }
}
