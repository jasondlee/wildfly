package org.wildfly.extension.opentelemetry.api;

import static org.wildfly.extension.opentelemetry.api.OpenTelemetryConfigurationConstants.DEFAULT_BATCH_DELAY;
import static org.wildfly.extension.opentelemetry.api.OpenTelemetryConfigurationConstants.DEFAULT_EXPORT_TIMEOUT;
import static org.wildfly.extension.opentelemetry.api.OpenTelemetryConfigurationConstants.DEFAULT_MAX_QUEUE_SIZE;
import static org.wildfly.extension.opentelemetry.api.OpenTelemetryConfigurationConstants.DEFAULT_RATIO;
import static org.wildfly.extension.opentelemetry.api.OpenTelemetryConfigurationConstants.PROTOCOL_GRPC;
import static org.wildfly.extension.opentelemetry.api.OpenTelemetryConfigurationConstants.PROTOCOL_HTTP;
import static org.wildfly.extension.opentelemetry.api.WildFlyOpenTelemetryConfig.OTEL_BSP_MAX_EXPORT_BATCH_SIZE;
import static org.wildfly.extension.opentelemetry.api.WildFlyOpenTelemetryConfig.OTEL_BSP_MAX_QUEUE_SIZE;
import static org.wildfly.extension.opentelemetry.api.WildFlyOpenTelemetryConfig.OTEL_BSP_SCHEDULE_DELAY;
import static org.wildfly.extension.opentelemetry.api.WildFlyOpenTelemetryConfig.OTEL_EXPORTER_OTLP_ENDPOINT;
import static org.wildfly.extension.opentelemetry.api.WildFlyOpenTelemetryConfig.OTEL_EXPORTER_OTLP_PROTOCOL;
import static org.wildfly.extension.opentelemetry.api.WildFlyOpenTelemetryConfig.OTEL_EXPORTER_OTLP_TIMEOUT;
import static org.wildfly.extension.opentelemetry.api.WildFlyOpenTelemetryConfig.OTEL_SERVICE_NAME;
import static org.wildfly.extension.opentelemetry.api.WildFlyOpenTelemetryConfig.OTEL_SPAN_PROCESSOR_TYPE;
import static org.wildfly.extension.opentelemetry.api.WildFlyOpenTelemetryConfig.OTEL_TRACES_EXPORTER;
import static org.wildfly.extension.opentelemetry.api.WildFlyOpenTelemetryConfig.OTEL_TRACES_SAMPLER;
import static org.wildfly.extension.opentelemetry.api.WildFlyOpenTelemetryConfig.OTEL_TRACES_SAMPLER_ARG;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.internal.http.HttpExporter;
import io.opentelemetry.exporter.otlp.internal.OtlpUserAgent;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.internal.SpiHelper;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessorBuilder;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import io.smallrye.opentelemetry.api.OpenTelemetryConfig;
import io.vertx.core.Vertx;
import org.wildfly.extension.opentelemetry.api.quarkus.VertxGrpcExporter;
import org.wildfly.extension.opentelemetry.api.quarkus.VertxHttpExporter;

public class WildFlyOpenTelemetryProducer {
    private final ConfigProperties configProperties;
    private final OpenTelemetryConfig otelConfig;

    public WildFlyOpenTelemetryProducer(OpenTelemetryConfig otelConfig) {
        this.otelConfig = otelConfig;
        this.configProperties = DefaultConfigProperties.create(otelConfig.properties());
    }

    public OpenTelemetry getOpenTelemetry() {
        String serviceName = configProperties.getString(OTEL_SERVICE_NAME);
        final SdkTracerProviderBuilder tracerProviderBuilder = SdkTracerProvider.builder()
                .addSpanProcessor(getSpanProcessor())
                .setResource(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, serviceName)));

        if (configProperties.getString(OTEL_TRACES_SAMPLER) != null) {
            Sampler sampler = getSampler();
            if (sampler != null) {
                tracerProviderBuilder.setSampler(sampler);
            }
        }

        try {
            return OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProviderBuilder.build())
                    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                    .build()
                    ;
        } catch (IllegalStateException ex) {
            return GlobalOpenTelemetry.get();
        }
    }

    private SpanProcessor getSpanProcessor() {
        final SpanExporter spanExporter = getSpanExporter();
        String processorType = configProperties.getString(OTEL_SPAN_PROCESSOR_TYPE, "batch");
        switch (processorType) {
            case "batch": {
                return getBatchSpanProcessor(spanExporter);
            }
            case "simple": {
                return SimpleSpanProcessor.create(spanExporter);
            }
            default: {
                throw new IllegalArgumentException("An unsupported span processor was specified: " + processorType);
            }
        }
    }

    private Sampler getSampler() {
        switch (configProperties.getString(OTEL_TRACES_SAMPLER, "on")) {
            case "on":
            case "always_on":
                return Sampler.alwaysOn();
            case "off":
            case "always_off":
                return Sampler.alwaysOff();
            case "ratio":
                return Sampler.traceIdRatioBased(configProperties.getDouble(OTEL_TRACES_SAMPLER_ARG, DEFAULT_RATIO));
            default:
                throw new IllegalArgumentException("Unrecognized value for sampler: " + configProperties.getString(OTEL_TRACES_SAMPLER));
        }
    }

    private BatchSpanProcessor getBatchSpanProcessor(SpanExporter spanExporter) {
        BatchSpanProcessorBuilder processorBuilder = BatchSpanProcessor.builder(spanExporter)
                .setScheduleDelay(Duration.ofMillis(configProperties.getInt(OTEL_BSP_SCHEDULE_DELAY, DEFAULT_BATCH_DELAY)))
                .setMaxQueueSize(configProperties.getInt(OTEL_BSP_MAX_QUEUE_SIZE, DEFAULT_MAX_QUEUE_SIZE))
                .setMaxExportBatchSize(configProperties.getInt(OTEL_BSP_MAX_EXPORT_BATCH_SIZE, DEFAULT_MAX_QUEUE_SIZE))
                .setExporterTimeout(Duration.ofMillis(configProperties.getInt(OTEL_EXPORTER_OTLP_TIMEOUT, DEFAULT_EXPORT_TIMEOUT)));
        return processorBuilder.build();
    }

    private SpanExporter getSpanExporter() {
        SpiHelper spiHelper = SpiHelper.create(otelConfig.getClass().getClassLoader());

        List<ConfigurableSpanExporterProvider> exporters = spiHelper.load(ConfigurableSpanExporterProvider.class);

        if (!exporters.isEmpty()) {
            return SpanExporter.composite(
                    exporters.stream()
                            .filter(p -> configProperties.getString(OTEL_TRACES_EXPORTER, "none").equals(p.getName()))
                            .map(p -> p.createExporter(configProperties)
                    ).collect(Collectors.toList())
            );
        } else {
            String endpoint = configProperties.getString(OTEL_EXPORTER_OTLP_ENDPOINT);
            String protocol = configProperties.getString(OTEL_EXPORTER_OTLP_PROTOCOL, PROTOCOL_GRPC);
            try {
                if (endpoint != null) {
                    URI uri = new URI(endpoint);
                    if (PROTOCOL_HTTP.equals(protocol)) {
                        return createHttpSpanExporter(uri);
                    } else if (PROTOCOL_GRPC.equals(protocol)){
                        return createOtlpGrpcSpanExporter(uri); // grpc
                    } else {
                        throw new IllegalArgumentException("Invalid exporter protocol: " + protocol);
                    }
                }
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Invalid exporter URL: " + endpoint);
            }
        }

        return new NoopSpanExporter();
    }

    private SpanExporter createOtlpGrpcSpanExporter(final URI baseUri) {
        return new VertxGrpcExporter(
                "otlp", // use the same as OTel does
                "span", // use the same as OTel does
                MeterProvider::noop,
                baseUri,
                true,
                Duration.ofSeconds(10),
                populateTracingExportHttpHeaders(),
                null,
                Vertx.vertx());
    }

    private SpanExporter createHttpSpanExporter(final URI baseUri) {
        return new VertxHttpExporter(
                new HttpExporter<>(
                        "otlp", // use the same as OTel does
                        "span", // use the same as OTel does
                        new VertxHttpExporter.VertxHttpSender(
                                baseUri,
                                true,

                                Duration.ofSeconds(10),
                                populateTracingExportHttpHeaders(),
                                "application/x-protobuf",
                                null,
                                Vertx.vertx()),
                        MeterProvider::noop,
                        false));
    }

    private Map<String, String> populateTracingExportHttpHeaders() {
        Map<String, String> headersMap = new HashMap<>();
        OtlpUserAgent.addUserAgentHeader(headersMap::put);
        return headersMap;
    }

    private static class NoopSpanExporter implements SpanExporter {
        @Override
        public CompletableResultCode export(Collection<SpanData> collection) {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }
}
