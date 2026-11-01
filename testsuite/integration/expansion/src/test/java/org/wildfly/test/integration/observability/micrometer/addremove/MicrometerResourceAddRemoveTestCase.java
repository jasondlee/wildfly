/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.integration.observability.micrometer.addremove;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.wildfly.test.integration.observability.setuptask.PrometheusSetupTask.PROMETHEUS_CONTEXT;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.List;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import org.apache.commons.io.input.Tailer;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.arquillian.testcontainers.api.TestcontainersRequired;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.arquillian.setup.SnapshotServerSetupTask;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.common.jms.JMSOperations;
import org.jboss.as.test.integration.common.jms.JMSOperationsProvider;
import org.jboss.as.test.shared.ServerReload;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.as.test.shared.observability.MessagingSubsystemSetupTask;
import org.jboss.as.test.shared.observability.NotificationProbeServlet;
import org.jboss.as.test.shared.observability.ResourceMetricsCheck;
import org.jboss.as.test.shared.observability.ServerLogTailerListener;
import org.jboss.as.test.shared.observability.setuptasks.RbacRealmSetupTask;
import org.jboss.as.test.shared.observability.signals.PrometheusMetric;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.wildfly.plugin.tools.server.ServerManager;
import org.wildfly.test.integration.observability.setuptask.PrometheusSetupTask;

/**
 * Verifies Micrometer registers metrics for resources added after boot, respects exposed-subsystem configuration, and
 * discovers dynamic resources under RBAC.
 */
@ExtendWith(ArquillianExtension.class)
@ServerSetup({
        SnapshotServerSetupTask.class,
        PrometheusSetupTask.class,
        MessagingSubsystemSetupTask.class,
        RbacRealmSetupTask.class})
@TestcontainersRequired
@RunAsClient
public class MicrometerResourceAddRemoveTestCase {
    private static final String DEPLOYMENT = "micrometer-notification-probe.war";
    private static final String QUEUE_NAME = "micrometer-resource-add-remove";
    private static final String QUEUE_JNDI_NAME = "java:/jms/queue/" + QUEUE_NAME;
    private static final int MESSAGE_COUNT = 10;
    private static final String METRIC_NAME = "messaging_activemq_message_count";
    private static final String FAILED_READ = "WFLYCTL0216";
    private static final String FAILED_OPERATION = "WFLYCTL0013";
    private static final String MANIFEST = "Dependencies: org.jboss.as.controller,org.jboss.as.server,org.jboss.msc\n";
    private static final String WEB_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="6.0">
                <servlet>
                    <servlet-name>notification-probe</servlet-name>
                    <servlet-class>""" + NotificationProbeServlet.class.getName() + """
                    </servlet-class>
                </servlet>
                <servlet-mapping>
                    <servlet-name>notification-probe</servlet-name>
                    <url-pattern>/</url-pattern>
                </servlet-mapping>
            </web-app>
            """;

    @ContainerResource
    private ManagementClient managementClient;

    @ContainerResource
    private ServerManager serverManager;

    @ArquillianResource
    private URL url;

    /** Creates the notification-probe deployment used by the test. */
    @Deployment(name = DEPLOYMENT, testable = false)
    public static Archive<?> deployment() {
        return ShrinkWrap.create(WebArchive.class, DEPLOYMENT)
                         .addClasses(NotificationProbeServlet.class)
                         .addAsManifestResource(new StringAsset(MANIFEST), "MANIFEST.MF")
                         .addAsWebInfResource(new StringAsset(WEB_XML), "web.xml");
    }

    /** Verifies meters appear for a queue added after boot and disappear after removal. */
    @Test
    public void addsAndRemovesResourceMetrics() throws Exception {
        runAddRemoveCycle(true);
    }

    /** Verifies dynamic collection queues only resources from exposed subsystems. */
    @Test
    public void onlyHandlesResourcesFromExposedSubsystems() throws Exception {
        try {
            setExposedSubsystems(List.of("undertow"));
            runAddRemoveCycle(false);
            setExposedSubsystems(List.of("messaging-activemq"));
            runAddRemoveCycle(true);
        } finally {
            setExposedSubsystems(List.of("*"));
        }
    }

    /** Verifies background resource discovery succeeds under RBAC while scrapes use the Monitor identity. */
    @Test
    public void addsAndRemovesResourceMetricsUnderRbac() throws Exception {
        try {
            setRbacProvider(true);
            runAddRemoveCycle(true);
        } finally {
            setRbacProvider(false);
        }
    }

    /** Adds a queue, verifies its metric state, and removes the queue. */
    private void runAddRemoveCycle(boolean queueMetricExpected) throws Exception {
        ServerLogTailerListener listener = new ServerLogTailerListener();
        JMSOperations jmsOperations = JMSOperationsProvider.getInstance(managementClient.getControllerClient());
        boolean queueCreated = false;
        try (Tailer ignored = Tailer.builder()
                                    .setFile(getServerLogFile())
                                    .setTailerListener(listener)
                                    .setDelayDuration(Duration.ofMillis(500))
                                    .get()) {
            clearNotifications();

            jmsOperations.createJmsQueue(QUEUE_NAME, QUEUE_JNDI_NAME);
            queueCreated = true;

            assertEventually(() -> assertNotification("resource-added"),
                    "JMS did not emit a resource-added notification for " + QUEUE_NAME);
            sendMessages();

            if (queueMetricExpected) {
                assertEventually(() -> assertQueueMetric(fetchMetrics(), true),
                        "JMS queue metric was not exported for " + QUEUE_NAME);
            } else {
                Thread.sleep(TimeoutUtil.adjust(1_000));
                assertQueueMetric(fetchMetrics(), false);
            }
            listener.logs.clear();

            jmsOperations.removeJmsQueue(QUEUE_NAME);
            queueCreated = false;
            assertEventually(() -> assertNotification("resource-removed"),
                    "JMS did not emit a resource-removed notification for " + QUEUE_NAME);
            assertEventually(() -> assertQueueMetric(fetchMetrics(), false),
                    "JMS queue metric was not removed for " + QUEUE_NAME);

        } finally {
            if (queueCreated) {
                jmsOperations.removeJmsQueue(QUEUE_NAME);
            }
            jmsOperations.close();
        }
        listener.assertNoFailure();
        assertNoQueueReadErrors(listener);
    }

    /** Changes the configured subsystem filter and reloads the Micrometer service. */
    private void setExposedSubsystems(List<String> subsystems) throws Exception {
        ModelNode value = new ModelNode();
        value.setEmptyList();
        subsystems.forEach(value::add);
        ModelNode operation = Operations.createWriteAttributeOperation(
                Operations.createAddress("subsystem", "micrometer"), "exposed-subsystems", value);
        ModelNode result = managementClient.getControllerClient().execute(operation);
        if (!Operations.isSuccessfulOutcome(result)) {
            throw new IllegalStateException(Operations.getFailureDescription(result).asString());
        }
        ServerReload.executeReloadAndWaitForCompletion(managementClient);
    }

    /** Enables or restores RBAC and Prometheus endpoint authentication. */
    private void setRbacProvider(boolean enabled) throws Exception {
        ModelNode operation = Util.createCompositeOperation(List.of(
                Operations.createWriteAttributeOperation(RbacRealmSetupTask.authorization.toModelNode(),
                        "provider", enabled ? "rbac" : "simple"),
                Operations.createWriteAttributeOperation(
                        Operations.createAddress("subsystem", "micrometer", "registry", "prometheus"),
                        "security-enabled", enabled)));
        ModelNode result = managementClient.getControllerClient().execute(operation);
        if (!Operations.isSuccessfulOutcome(result)) {
            throw new IllegalStateException(Operations.getFailureDescription(result).asString());
        }
        ServerReload.executeReloadAndWaitForCompletion(managementClient);
    }

    /** Sends probe messages so the queue meter has a non-zero value. */
    private void sendMessages() throws Exception {
        String requestUrl = url + "?send=" + QUEUE_NAME;
        try (Client client = ClientBuilder.newClient()) {
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                try (Response response = client.target(requestUrl).request().get()) {
                    String responseBody = response.readEntity(String.class);
                    assertEquals(200,
                            response.getStatus(), "Message producer returned " + response.getStatus() + ": " + responseBody);
                }
            }
        }
    }

    /** Clears notifications recorded by the probe deployment. */
    private void clearNotifications() throws Exception {
        fetchNotifications(true);
    }

    /** Asserts that the probe recorded the requested lifecycle notification. */
    private void assertNotification(String type) throws Exception {
        List<String> notifications = fetchNotifications(false);
        assertTrue(notifications.stream().anyMatch(notification -> notification.startsWith(type + "|")
                        && notification.contains("jms-queue=" + QUEUE_NAME)),
                String.format("Notifications did not contain %s for %s: %s", type, QUEUE_NAME, notifications));
    }

    /** Fetches notifications from the probe deployment. */
    private List<String> fetchNotifications(boolean clear) throws Exception {
        String requestUrl = url.toString();
        if (clear) {
            requestUrl += "?clear=true";
        }
        try (Client client = ClientBuilder.newClient();
             Response response = client.target(requestUrl).request().get()) {
            assertEquals(200,
                    response.getStatus(), "Notification probe returned " + response.getStatus());
            String body = response.readEntity(String.class);
            return body.isEmpty() ? List.of() : List.of(body.split("\\R"));
        }
    }

    /** Asserts whether the queue meter is present in the endpoint response. */
    private static void assertQueueMetric(List<PrometheusMetric> metrics, boolean expected) {
        boolean found = metrics.stream().anyMatch(metric -> METRIC_NAME.equals(metric.getKey())
                && "jms-queue".equals(metric.getTags().get("type"))
                && QUEUE_NAME.equals(metric.getTags().get("name")));
        assertEquals(expected, found, "Unexpected JMS queue metric state: " + metrics);
    }

    /** Asserts that collection did not log a read or operation failure for the queue. */
    private static void assertNoQueueReadErrors(ServerLogTailerListener listener) {
        assertFalse(listener.logs.stream().anyMatch(line -> line.contains(QUEUE_NAME)
                        && (line.contains(FAILED_READ) || line.contains(FAILED_OPERATION))),
                "Queue meter collection logged a read failure: " + listener.logs);
    }

    /** Resolves the server log path from the management model. */
    private File getServerLogFile() throws IOException {
        ModelNode address = Operations.createAddress("path", "jboss.server.log.dir");
        ModelNode operation = Operations.createReadAttributeOperation(address, "path");
        return new File(serverManager.executeOperation(operation).asString(), "server.log");
    }

    /** Fetches and parses the Micrometer Prometheus endpoint. */
    private List<PrometheusMetric> fetchMetrics() throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpClientContext context = HttpClientContext.create();
            CredentialsProvider credentials = new BasicCredentialsProvider();
            credentials.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(
                    RbacRealmSetupTask.USER_MONITOR, RbacRealmSetupTask.TEST_RBAC_PASSWORD));
            context.setCredentialsProvider(credentials);

            HttpGet request = new HttpGet(String.format("http://%s:%d%s", managementClient.getMgmtAddress(),
                    managementClient.getMgmtPort(), PROMETHEUS_CONTEXT));
            request.setConfig(RequestConfig.custom()
                    .setTargetPreferredAuthSchemes(List.of(AuthSchemes.DIGEST))
                    .build());
            try (CloseableHttpResponse response = client.execute(request, context)) {
                assertEquals(200, response.getStatusLine().getStatusCode(), "Metrics endpoint returned an error");
                return PrometheusMetric.buildPrometheusMetrics(EntityUtils.toString(response.getEntity()));
            }
        }
    }

    /** Repeats an eventually-consistent assertion until it succeeds or times out. */
    private static void assertEventually(ResourceMetricsCheck check, String message) throws Exception {
        AssertionError failure = new AssertionError(message);
        long end = System.currentTimeMillis() + TimeoutUtil.adjust(30_000);
        while (System.currentTimeMillis() < end) {
            try {
                check.evaluate();
                return;
            } catch (AssertionError e) {
                failure = e;
                Thread.sleep(TimeoutUtil.adjust(1_000));
            }
        }
        throw failure;
    }


}
