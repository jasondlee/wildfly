package org.wildfly.extension.opentelemetry.api;

import io.smallrye.opentelemetry.api.OpenTelemetryConfig;
import io.smallrye.opentelemetry.implementation.cdi.OpenTelemetryProducer;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessBean;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.enterprise.inject.spi.ProcessInjectionTarget;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.inject.spi.ProcessProducer;
import jakarta.enterprise.inject.spi.ProcessProducerField;
import jakarta.enterprise.inject.spi.ProcessProducerMethod;
import jakarta.enterprise.inject.spi.ProcessSyntheticBean;
import jakarta.inject.Singleton;

public class OpenTelemetryCdiExtension implements Extension {
    private final OpenTelemetryConfig config;

    public OpenTelemetryCdiExtension(OpenTelemetryConfig config) {
        this.config = config;
    }

    public void foo(@Observes BeforeBeanDiscovery bbd) {
    }

    public void processBean(@Observes ProcessBean pb) {
        System.err.println("Processing bean: " + pb.getBean().getBeanClass().getCanonicalName());
    }

    public void processInjectionPoint(@Observes ProcessInjectionPoint ip) {
        System.err.println("Processing injection point: " + ip.getInjectionPoint().toString());
    }
    public void processInjectionTarget(@Observes ProcessInjectionTarget it) {
        System.err.println("Processing injection target: " + it.getInjectionTarget().toString());
    }
    public void processManagedBean(@Observes ProcessManagedBean mb) {
        System.err.println("Processing managed bean: " + mb.getAnnotatedBeanClass().getJavaClass());
    }
    public void processProducer(@Observes ProcessProducer p) {
        System.err.println("Processing producer: " + p.getProducer().toString());
    }
    public void processProducerField(@Observes ProcessProducerField p) {
        System.err.println("Processing producer field: " + p.getAnnotatedProducerField().toString());
    }
    public void processProducerMethod(@Observes ProcessProducerMethod p) {
        System.err.println("Processing producer method: " + p.getAnnotatedProducerMethod().getJavaMember());
    }
    public void processSyntheticBean(@Observes ProcessSyntheticBean p) {
        System.err.println("Processing synthetic bean: " + p);
    }

    public void registerOtelConfigBean(@Observes AfterBeanDiscovery abd, BeanManager beanManager) {
        try {
            // If this class is found, then we don't need to inject our server-config-based configuration, as it's
            // provided by the smallrye-opentelemetr-config module and MP Config
            // TODO: Use a capability check for this
            Class.forName("io.smallrye.opentelemetry.implementation.config.OpenTelemetryConfigProducer");
        } catch (ClassNotFoundException cnfe) {
//            System.out.println("Adding bean: " + OpenTelemetryConfigProducer.class.getCanonicalName());
//            abd.addBean()
//                    .scope(Singleton.class)
//                    .addQualifier(Default.Literal.INSTANCE)
////                    .types(OpenTelemetryConfig.class)
//                    .addTransitiveTypeClosure(OpenTelemetryConfig.class)
//                    .createWith(e -> {
//                        System.out.println("Creating OpenTelemetryConfigProducer");
//                        return new OpenTelemetryConfigProducer(config);
//                    });

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

//        abd.addBean()
//                .scope(Singleton.class)
////                    .types(OpenTelemetryConfig.class)
//                .addTransitiveTypeClosure(WildFlyOpenTelemetryProducer.class)
//                .produceWith(e -> {
//                    System.out.println("'Creating' producer");
//                    return new WildFlyOpenTelemetryProducer(config);
//                });
    }

    public static class WildFlyOpenTelemetryProducer extends OpenTelemetryProducer {
        public WildFlyOpenTelemetryProducer(OpenTelemetryConfig config) {
            this.config = config;
        }
    }
}
