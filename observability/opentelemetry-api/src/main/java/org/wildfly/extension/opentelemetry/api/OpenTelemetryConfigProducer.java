package org.wildfly.extension.opentelemetry.api;

import io.smallrye.opentelemetry.api.OpenTelemetryConfig;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
public class OpenTelemetryConfigProducer {
    private OpenTelemetryConfig config;

    public OpenTelemetryConfigProducer(OpenTelemetryConfig config) {
        this.config = config;
    }

    @Produces
    @Singleton
    @Default
    public OpenTelemetryConfig getConfig() {
        return config;
    }
}
