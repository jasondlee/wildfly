package org.wildfly.extension.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.smallrye.opentelemetry.implementation.cdi.OpenTelemetryProducer;
import org.wildfly.extension.opentelemetry.api.WildFlyOpenTelemetryConfig;

public class OpenTelemetryService {
    private final WildFlyOpenTelemetryConfig openTelemetryConfig;
    private OpenTelemetry openTelemetry;

    public OpenTelemetryService(WildFlyOpenTelemetryConfig openTelemetryConfig) {
        this.openTelemetryConfig = openTelemetryConfig;
    }

    public WildFlyOpenTelemetryConfig getOpenTelemetryConfig() {
        return openTelemetryConfig;
    }

    public void start() {
        openTelemetry = new OpenTelemetryProducer().getOpenTelemetry(openTelemetryConfig);
    }
}
