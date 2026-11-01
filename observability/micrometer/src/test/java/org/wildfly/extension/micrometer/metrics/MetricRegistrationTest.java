/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.micrometer.metrics;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Gauge;
import org.jboss.as.controller.LocalModelControllerClient;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.wildfly.extension.micrometer.prometheus.WildFlyPrometheusRegistry;
import org.wildfly.extension.micrometer.registry.WildFlyCompositeRegistry;
import org.wildfly.extension.micrometer.registry.WildFlyRegistry;

/** Verifies Micrometer metric removal and registration-task synchronization. */
public class MetricRegistrationTest {
    /** Verifies scrape client scopes restore nested and exceptional thread-local state. */
    @Test
    public void metricClientScopeRestoresPreviousClient() {
        LocalModelControllerClient firstClient = createClient();
        LocalModelControllerClient nestedClient = createClient();
        assertNull(WildFlyRegistry.getCurrentModelControllerClient());

        try {
            WildFlyRegistry.withModelControllerClient(firstClient, () -> {
                assertSame(firstClient, WildFlyRegistry.getCurrentModelControllerClient());
                WildFlyRegistry.withModelControllerClient(nestedClient, () -> {
                    assertSame(nestedClient, WildFlyRegistry.getCurrentModelControllerClient());
                    return null;
                });
                assertSame(firstClient, WildFlyRegistry.getCurrentModelControllerClient());
                throw new IllegalStateException("expected");
            });
            fail("Expected scoped action to fail");
        } catch (IllegalStateException expected) {
            assertEquals("expected", expected.getMessage());
        }

        assertNull(WildFlyRegistry.getCurrentModelControllerClient());
    }

    /** Verifies a registry callback reads a metric with the client scoped by its exporter. */
    @Test
    public void metricValueUsesScopedClient() {
        PathAddress address = PathAddress.EMPTY_ADDRESS;
        try (WildFlyPrometheusRegistry registry = new WildFlyPrometheusRegistry()) {
            MetricRegistration registration = new MetricRegistration(registry);
            registration.registerMetric(new WildFlyMetric(address, "metric"), metadata(address));
            Gauge gauge = (Gauge) registry.getMeters().iterator().next();
            double value = WildFlyRegistry.withModelControllerClient(createClient(), gauge::value);

            assertEquals(42.0, value, 0.0);
        }
    }

    /** Verifies removing a resource also removes meters for its children. */
    @Test
    public void unregistersMetricsForRemovedResourceAndChildren() {
        WildFlyCompositeRegistry registry = new WildFlyCompositeRegistry();
        MetricRegistration registration = new MetricRegistration(registry);
        PathAddress resource = PathAddress.pathAddress("subsystem", "test");

        registration.registerMetric(new WildFlyMetric(resource, "metric"), metadata(resource));
        registration.registerMetric(new WildFlyMetric(resource.append("child", "one"), "metric"),
                metadata(resource.append("child", "one")));
        assertEquals("Both resource meters should be registered", 2, registry.getMeters().size());

        registration.unregister(resource);

        assertEquals("Removing the resource should remove its meters", 0, registry.getMeters().size());
    }

    /** Verifies adding a registration task waits for an active registration pass. */
    @Test
    public void addingRegistrationTaskWaitsForRegistration() throws Exception {
        WildFlyCompositeRegistry registry = new WildFlyCompositeRegistry();
        MetricRegistration registration = new MetricRegistration(registry);
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        CountDownLatch taskAdded = new CountDownLatch(1);

        registration.addRegistrationTask(() -> {
            taskStarted.countDown();
            try {
                releaseTask.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread registerThread = new Thread(registration::register);
        registerThread.start();
        assertTrue("Registration task did not start", taskStarted.await(5, TimeUnit.SECONDS));

        Thread addThread = new Thread(() -> {
            registration.addRegistrationTask(() -> { });
            taskAdded.countDown();
        });
        addThread.start();

        try {
            assertFalse("Adding a task should wait for registration", taskAdded.await(100, TimeUnit.MILLISECONDS));
        } finally {
            releaseTask.countDown();
        }

        assertTrue("Queued task was not added after registration", taskAdded.await(5, TimeUnit.SECONDS));
        registerThread.join(5_000);
        addThread.join(5_000);
    }

    /** Creates metadata for a test meter at the supplied address. */
    private static WildFlyMetricMetadata metadata(PathAddress address) {
        return new WildFlyMetricMetadata("metric", address, "description", MeasurementUnit.NONE, MetricMetadata.Type.GAUGE);
    }

    /** Creates a client stub that returns a defined metric value. */
    private static LocalModelControllerClient createClient() {
        ModelNode response = new ModelNode();
        response.get(OUTCOME).set(SUCCESS);
        response.get(RESULT).set(42.0);
        return (LocalModelControllerClient) Proxy.newProxyInstance(
                LocalModelControllerClient.class.getClassLoader(),
                new Class<?>[] { LocalModelControllerClient.class },
                (proxy, method, arguments) -> "execute".equals(method.getName()) ? response : null);
    }
}
