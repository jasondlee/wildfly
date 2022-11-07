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

package org.wildfly.extension.opentelemetry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.smallrye.opentelemetry.api.OpenTelemetryConfig;

class WildFlyOpenTelemetryConfig implements OpenTelemetryConfig {
    Map<String, String> properties;

    private WildFlyOpenTelemetryConfig(String serviceName, String exporter, String endpoint, String spanProcessor,
                                       String batchDelay, String maxQueueSize, String maxExportBatchSize,
                                       String exportTimeout, String sampler, String ratio) {
        Map<String, String> config = new HashMap<>();
        // TODO: Create constants for these. Oddly, the otel API does not provide any
        config.put("otel.service.name", serviceName);
        config.put("otel.traces.exporter", exporter);
        switch (endpoint) {
            case "jaeger": config.put("otel.exporter.jaeger.endpoint", endpoint);
                           config.put("otel.exporter.jaeger.timeout", exportTimeout);
                           break;
            case "otlp": config.put("otel.exporter.otlp.endpoint", endpoint);
                         config.put("otel.exporter.otlp.timeout", exportTimeout);
                         break;
        }

        config.put("otel.bsp.schedule.delay", batchDelay);
        config.put("otel.bsp.max.queue.size", maxQueueSize);
        config.put("otel.bsp.max.export.batch.size", maxExportBatchSize);
        config.put("otel.traces.sampler", sampler);
        config.put("otel.traces.sampler.arg", ratio);

        properties = Collections.unmodifiableMap(config);
    }

    @Override
    public Map<String, String> properties() {
        return properties;
    }

    static class OpenTelemetryConfigBuilder {
        private String serviceName;
        private String exporter;
        private String endpoint;
        private String spanProcessor;
        private String batchDelay;
        private String maxQueueSize;
        private String maxExportBatchSize;
        private String exportTimeout;
        private String sampler;
        private String ratio;

        private OpenTelemetryConfigBuilder() {
        }

        static OpenTelemetryConfigBuilder config() {
            return new OpenTelemetryConfigBuilder();
        }

        OpenTelemetryConfigBuilder withServiceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        OpenTelemetryConfigBuilder withExporter(String exporter) {
            this.exporter = exporter;
            return this;
        }

        OpenTelemetryConfigBuilder withEndpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        OpenTelemetryConfigBuilder withSpanProcessor(String spanProcessor) {
            this.spanProcessor = spanProcessor;
            return this;
        }

        OpenTelemetryConfigBuilder withBatchDelay(String batchDelay) {
            this.batchDelay = batchDelay;
            return this;
        }

        OpenTelemetryConfigBuilder withMaxQueueSize(String maxQueueSize) {
            this.maxQueueSize = maxQueueSize;
            return this;
        }

        OpenTelemetryConfigBuilder withMaxExportBatchSize(String maxExportBatchSize) {
            this.maxExportBatchSize = maxExportBatchSize;
            return this;
        }

        OpenTelemetryConfigBuilder withExportTimeout(String exportTimeout) {
            this.exportTimeout = exportTimeout;
            return this;
        }

        OpenTelemetryConfigBuilder withSampler(String sampler) {
            this.sampler = sampler;
            return this;
        }

        OpenTelemetryConfigBuilder withRatio(String ratio) {
            this.ratio = ratio;
            return this;
        }

        WildFlyOpenTelemetryConfig build() {
            return new WildFlyOpenTelemetryConfig(serviceName, exporter, endpoint, spanProcessor, batchDelay, maxQueueSize,
                    maxExportBatchSize, exportTimeout, sampler, ratio);
        }
    }
}
