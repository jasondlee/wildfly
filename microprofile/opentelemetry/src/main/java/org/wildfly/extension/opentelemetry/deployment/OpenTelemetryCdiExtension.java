package org.wildfly.extension.opentelemetry.deployment;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeforeShutdown;
import javax.enterprise.inject.spi.Extension;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.wildfly.security.manager.WildFlySecurityManager;

public class OpenTelemetryCdiExtension implements Extension {
    private static final Map<ClassLoader, OpenTelemetry> OTEL_INSTANCES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ClassLoader, Tracer> TRACERS = Collections.synchronizedMap(new WeakHashMap<>());

    public static void registerApplicationOpenTelemetryBean(ClassLoader classLoader, OpenTelemetry bean) {
        OTEL_INSTANCES.put(classLoader, bean);
    }

    public static void registerApplicationTracer(ClassLoader classLoader, Tracer tracer) {
        TRACERS.put(classLoader, tracer);
    }

    public void registerOpenTelemetryBeans(@Observes AfterBeanDiscovery abd) {
        abd.addBean().addTransitiveTypeClosure(OpenTelemetry.class).produceWith(i ->
                OTEL_INSTANCES.get(WildFlySecurityManager.getCurrentContextClassLoaderPrivileged()));
        abd.addBean().addTransitiveTypeClosure(Tracer.class).produceWith(i ->
                TRACERS.get(WildFlySecurityManager.getCurrentContextClassLoaderPrivileged()));
    }

    public void beforeShutdown(@Observes final BeforeShutdown bs) {
        OTEL_INSTANCES.remove(WildFlySecurityManager.getCurrentContextClassLoaderPrivileged());
        TRACERS.remove(WildFlySecurityManager.getCurrentContextClassLoaderPrivileged());
    }
}
