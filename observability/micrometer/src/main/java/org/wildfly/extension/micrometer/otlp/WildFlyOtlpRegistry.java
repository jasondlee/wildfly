/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.micrometer.otlp;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import io.micrometer.core.instrument.Clock;
import io.micrometer.registry.otlp.AggregationTemporality;
import io.micrometer.registry.otlp.HistogramFlavor;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import org.jboss.as.controller.LocalModelControllerClient;
import org.jboss.as.controller.ModelControllerClientFactory;
import org.jboss.as.controller.access.InVmAccess;
import org.wildfly.extension.micrometer.WildFlyMicrometerConfig;
import org.wildfly.extension.micrometer.registry.WildFlyRegistry;
import org.wildfly.security.manager.WildFlySecurityManager;

public class WildFlyOtlpRegistry extends OtlpMeterRegistry implements WildFlyRegistry {
    private final ModelControllerClientFactory modelControllerClientFactory;
    private final Executor executor;

    /** Creates an OTLP registry that can read metrics as SuperUser during background pushes. */
    public WildFlyOtlpRegistry(WildFlyMicrometerOtlpConfig config,
            ModelControllerClientFactory modelControllerClientFactory, Executor executor) {
        super(config, Clock.SYSTEM);
        this.modelControllerClientFactory = modelControllerClientFactory;
        this.executor = executor;
    }

    /**
     * [WFLY-20566] OTLP exports have no caller identity, so each push uses a short-lived SuperUser client. The request
     * scope lets the shared metric callbacks use that client while the existing in-VM access preserves push behavior.
     */
    @Override
    protected void publish() {
        try (LocalModelControllerClient client = modelControllerClientFactory.createSuperUserClient(executor)) {
            WildFlyRegistry.withModelControllerClient(client, () -> {
                InVmAccess.runInVm((PrivilegedAction<Void>) () -> {
                    super.publish();
                    return null;
                });
                return null;
            });
        }
    }

    public static class WildFlyMicrometerOtlpConfig extends WildFlyMicrometerConfig implements OtlpConfig {
        /**
         * The OTLP endpoint to which to push metrics
         */
        private String endpoint;
        /**
         * How frequently, in seconds, to push metrics
         */
        private Long step;
        private List<String> exposedSubsystems;

        // Use Builder
        private WildFlyMicrometerOtlpConfig() {}

        @Override
        public String url() {
            return endpoint;
        }

        @Override
        public Duration step() {
            return Duration.ofSeconds(step);
        }


        @Override
        public String get(String key) {
            return null; // Accept defaults not explicitly overridden below
        }

        @Override
        public Map<String, String> resourceAttributes() {
            Map<String, String> attributes = OtlpConfig.super.resourceAttributes();
            if (!attributes.containsKey("service.name")) {
                attributes.put("service.name", "wildfly");
            }
            return attributes;
        }

        @Override
        public AggregationTemporality aggregationTemporality() {
            if (WildFlySecurityManager.isChecking()) {
                return AccessController.doPrivileged((PrivilegedAction<AggregationTemporality>) OtlpConfig.super::aggregationTemporality);
            } else {
                return OtlpConfig.super.aggregationTemporality();
            }
        }

        @Override
        public HistogramFlavor histogramFlavor() {
            if (WildFlySecurityManager.isChecking()) {
                return AccessController.doPrivileged((PrivilegedAction<HistogramFlavor>) OtlpConfig.super::histogramFlavor);
            } else {
                return OtlpConfig.super.histogramFlavor();
            }
        }

        @Override
        public Map<String, String> headers() {
            if (WildFlySecurityManager.isChecking()) {
                return AccessController.doPrivileged((PrivilegedAction<Map<String, String>>) OtlpConfig.super::headers);
            } else {
                return OtlpConfig.super.headers();
            }
        }

        public static class Builder {
            private final WildFlyMicrometerOtlpConfig config = new WildFlyMicrometerOtlpConfig();

            public WildFlyMicrometerOtlpConfig.Builder endpoint(String endpoint) {
                config.endpoint = endpoint;
                return this;
            }

            public WildFlyMicrometerOtlpConfig.Builder step(Long step) {
                config.step = step;
                return this;
            }

            public WildFlyMicrometerOtlpConfig build() {
                return config;
            }
        }
    }
}
