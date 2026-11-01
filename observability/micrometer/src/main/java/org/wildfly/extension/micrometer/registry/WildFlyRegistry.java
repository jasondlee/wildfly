/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.micrometer.registry;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.jboss.as.controller.LocalModelControllerClient;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.wildfly.extension.micrometer.metrics.MetricMetadata;
import org.wildfly.extension.micrometer.metrics.WildFlyMetric;

public interface WildFlyRegistry extends AutoCloseable {
    Meter remove(Meter.Id mappedId);
    void close();

    /**
     * Makes the exporter client available while a scrape or publish reads meter values.
     *
     * @param client the client for this export
     * @param action the scrape or publish action
     * @param <T> the result type
     * @return the action result
     */
    static <T> T withModelControllerClient(LocalModelControllerClient client, Supplier<T> action) {
        return ModelControllerClientContext.with(client, action);
    }

    /** Returns the client currently scoped to a scrape or publish on this thread. */
    static LocalModelControllerClient getCurrentModelControllerClient() {
        return ModelControllerClientContext.current();
    }

    default Meter.Id addMeter(WildFlyMetric metric, MetricMetadata metadata) {
        return switch (metadata.getType()) {
            case GAUGE -> addGauge(metric, metadata);
            case COUNTER -> addCounter(metric, metadata);
        };
    }

    private Meter.Id addCounter(WildFlyMetric metric, MetricMetadata metadata) {
        return FunctionCounter.builder(metadata.getMetricName(), metric,
                        value -> getMetricValue(metric, metadata.getMeasurementUnit()))
                .tags(getTags(metadata))
                .baseUnit(getBaseUnit(metadata))
                .description(metadata.getDescription())
                .register((MeterRegistry) this)
                .getId();
    }

    private Meter.Id addGauge(WildFlyMetric metric, MetricMetadata metadata) {
        return Gauge.builder(metadata.getMetricName(), metric,
                        value -> getMetricValue(metric, metadata.getMeasurementUnit()))
                .tags(getTags(metadata))
                .baseUnit(getBaseUnit(metadata))
                .description(metadata.getDescription())
                .register((MeterRegistry) this)
                .getId();
    }

    private Tags getTags(MetricMetadata metadata) {
        return Tags.of(Arrays.stream(metadata.getTags())
                .map(t -> Tag.of(t.getKey(), t.getValue()))
                .collect(Collectors.toList()));
    }

    private String getBaseUnit(MetricMetadata metadata) {
        String measurementUnit = metadata.getBaseMetricUnit();
        return "none".equalsIgnoreCase(measurementUnit) ? null : measurementUnit.toLowerCase(Locale.ENGLISH);
    }

    private double getMetricValue(WildFlyMetric metric, MeasurementUnit unit) {
        OptionalDouble metricValue = metric.getValue(getCurrentModelControllerClient());
        return metricValue.isPresent() ?
                scaleToBaseUnit(metricValue.getAsDouble(), unit) :
                0.0;
    }

    private double scaleToBaseUnit(double value, MeasurementUnit unit) {
        return value * MeasurementUnit.calculateOffset(unit, unit.getBaseUnits());
    }
}

/** Holds a thread-scoped client while Micrometer synchronously samples registered meters. */
final class ModelControllerClientContext {
    private static final ThreadLocal<LocalModelControllerClient> CURRENT_CLIENT = new ThreadLocal<>();

    /** Prevents instantiation of this thread-local context holder. */
    private ModelControllerClientContext() {
    }

    /** Runs an exporter action with its client scoped to the current thread. */
    static <T> T with(LocalModelControllerClient client, Supplier<T> action) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(action, "action");
        LocalModelControllerClient previous = CURRENT_CLIENT.get();
        CURRENT_CLIENT.set(client);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT_CLIENT.remove();
            } else {
                CURRENT_CLIENT.set(previous);
            }
        }
    }

    /** Returns the client scoped to the current thread, if any. */
    static LocalModelControllerClient current() {
        return CURRENT_CLIENT.get();
    }
}
