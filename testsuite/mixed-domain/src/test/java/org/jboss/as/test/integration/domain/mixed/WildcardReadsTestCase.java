/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.mixed;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTO_START;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INET_ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PORT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.QUERY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SELECT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WHERE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.ValueExpression;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for cross-process wildcard reads in a mixed domain. See https://issues.jboss.org/browse/WFCORE-621.
 *
 * @author Brian Stansberry
 */
public class WildcardReadsTestCase {

    private static final PathElement HOST_WILD = PathElement.pathElement(HOST);
    private static final PathElement HOST_PRIMARY = PathElement.pathElement(HOST, "primary");
    private static final PathElement HOST_SECONDARY = PathElement.pathElement(HOST, "secondary");
    private static final PathElement SERVER_WILD = PathElement.pathElement(RUNNING_SERVER);
    private static final PathElement SERVER_ONE = PathElement.pathElement(RUNNING_SERVER, "server-one");
    private static final PathElement INTERFACE_WILD = PathElement.pathElement(INTERFACE);
    private static final PathElement INTERFACE_PUBLIC = PathElement.pathElement(INTERFACE, "public");
    private static final PathElement SERVER_CONFIG_WILD = PathElement.pathElement(SERVER_CONFIG);
    private static final PathElement SERVER_CONFIG_ONE = PathElement.pathElement(SERVER_CONFIG, "server-one");
    private static final PathAddress SOCKET_BINDING_HTTP = PathAddress.pathAddress(PathElement.pathElement(SOCKET_BINDING_GROUP, "standard-sockets"), PathElement.pathElement(SOCKET_BINDING, "http"));
    private static final ValueExpression PRIMARY_ADDRESS = new ValueExpression("${jboss.test.host.primary.address}");
    private static final ValueExpression SECONDARY_ADDRESS = new ValueExpression("${jboss.test.host.secondary.address}");

    private static final Set<String> VALID_STATES = new HashSet<>(Arrays.asList("running", "stopped"));

    private static DomainTestSupport support;
    private static Boolean primaryServerOneStarted;
    protected static Version.AsVersion version;

    @Before
    public void init() throws Exception {
        support = KernelBehaviorTestSuite.getSupport(this.getClass());
        version = this.getClass().getAnnotation(Version.class).value();

        if (primaryServerOneStarted == null) {
            String state = readPrimaryServerOneState();
            primaryServerOneStarted = "running".equalsIgnoreCase(state);
        }
        if (!primaryServerOneStarted) {
            ModelNode op = Util.createEmptyOperation("start", PathAddress.pathAddress(HOST_PRIMARY, SERVER_CONFIG_ONE));
            executeForResult(op, ModelType.STRING);

            String state;
            long timeout = System.currentTimeMillis() + TimeoutUtil.adjust(30000);
            while (!"running".equalsIgnoreCase(state = readPrimaryServerOneState())
                    && System.currentTimeMillis() < timeout) {
                TimeUnit.MILLISECONDS.sleep(25);
            }
            assertNotNull("Could not start primary/server-one", state);
            assertEquals("Could not start primary/server-one", "running", state.toLowerCase(Locale.ENGLISH));
        }
    }

    private static String readPrimaryServerOneState() throws IOException {
        ModelNode op = Util.getReadAttributeOperation(PathAddress.pathAddress(HOST_PRIMARY, SERVER_ONE), "server-state");
        ModelNode response = support.getDomainPrimaryLifecycleUtil().getDomainClient().execute(op);
        if (SUCCESS.equals(response.get(OUTCOME).asString())) {
            return response.get(RESULT).asString();
        }
        return null;
    }

    @AfterClass
    public static synchronized void afterClass() {
        if (primaryServerOneStarted == Boolean.FALSE) {
            ModelNode op = Util.createEmptyOperation("stop", PathAddress.pathAddress(HOST_PRIMARY, SERVER_CONFIG_ONE));
            executeForResult(op, ModelType.STRING);
        }
        KernelBehaviorTestSuite.afterClass();
    }

    @Test
    public void testAllHostsAllServersReadInterfaceResources() {
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, PathAddress.pathAddress(HOST_WILD, SERVER_WILD, INTERFACE_WILD));
        ModelNode resp = executeForResult(op);
        assertEquals(resp.toString(), 6, resp.asInt());
        int primaryCount = 0;
        for (ModelNode item : resp.asList()) {
            if (isPrimaryItem(item, 3)) {
                primaryCount++;
            }
            assertEquals(item.toString(), ModelType.EXPRESSION, item.get(RESULT, INET_ADDRESS).getType());
        }
        assertEquals(resp.toString(), 3, primaryCount);
    }

    @Test
    public void testSecondaryAllServersReadInterfaceResources() {
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, PathAddress.pathAddress(HOST_SECONDARY, SERVER_WILD, INTERFACE_WILD));
        ModelNode resp = executeForResult(op);
        assertEquals(resp.toString(), 3, resp.asInt());
        int primaryCount = 0;
        for (ModelNode item : resp.asList()) {
            if (isPrimaryItem(item, 3)) {
                primaryCount++;
            }
            assertEquals(item.toString(), ModelType.EXPRESSION, item.get(RESULT, INET_ADDRESS).getType());
        }
        assertEquals(resp.toString(), 0, primaryCount);
    }

    @Test
    public void testAllHostsAllServersReadRootResource() {
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, PathAddress.pathAddress(HOST_WILD, SERVER_WILD));
        op.get(INCLUDE_RUNTIME).set(true);
        ModelNode resp = executeForResult(op);
        assertEquals(resp.toString(), expectUnstartedServerResource() ? 4 : 3, resp.asInt());
        int primaryCount = 0;
        for (ModelNode item : resp.asList()) {
            if (isPrimaryItem(item, 2)) {
                primaryCount++;
            }
            assertTrue(item.toString(), VALID_STATES.contains(item.get(RESULT, "server-state").asString().toLowerCase(Locale.ENGLISH)));
        }
        assertEquals(resp.toString(), 2, primaryCount);
    }

    @Test
    public void testSecondaryAllServersReadRootResource() {
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, PathAddress.pathAddress(HOST_SECONDARY, SERVER_WILD));
        op.get(INCLUDE_RUNTIME).set(true);
        ModelNode resp = executeForResult(op);
        assertEquals(resp.toString(), expectUnstartedServerResource() ? 2 : 1, resp.asInt());
        int primaryCount = 0;
        for (ModelNode item : resp.asList()) {
            if (isPrimaryItem(item, 2)) {
                primaryCount++;
            }
            assertTrue(item.toString(), VALID_STATES.contains(item.get(RESULT, "server-state").asString().toLowerCase(Locale.ENGLISH)));
        }
        assertEquals(resp.toString(), 0, primaryCount);
    }

    @Test
    public void testAllHostsAllServersReadInterfaceAttribute() {
        ModelNode op = Util.createEmptyOperation(READ_ATTRIBUTE_OPERATION, PathAddress.pathAddress(HOST_WILD, SERVER_WILD, INTERFACE_PUBLIC));
        op.get(NAME).set("inet-address");
        ModelNode resp = executeForResult(op);
        assertEquals(resp.toString(), 2, resp.asInt());
        int primaryCount = 0;
        for (ModelNode item : resp.asList()) {
            if (isPrimaryItem(item, 3)) {
                primaryCount++;
            }
            assertEquals(item.toString(), ModelType.EXPRESSION, item.get(RESULT).getType());
        }
        assertEquals(resp.toString(), 1, primaryCount);
    }

    @Test
    public void testSecondaryAllServersReadInterfaceAttribute() {
        ModelNode op = Util.createEmptyOperation(READ_ATTRIBUTE_OPERATION, PathAddress.pathAddress(HOST_SECONDARY, SERVER_WILD, INTERFACE_PUBLIC));
        op.get(NAME).set("inet-address");
        ModelNode resp = executeForResult(op);
        assertEquals(resp.toString(), 1, resp.asInt());
        int primaryCount = 0;
        for (ModelNode item : resp.asList()) {
            if (isPrimaryItem(item, 3)) {
                primaryCount++;
            }
            assertEquals(item.toString(), ModelType.EXPRESSION, item.get(RESULT).getType());
        }
        assertEquals(resp.toString(), 0, primaryCount);

    }

    @Test
    public void testAllHostsAllServersReadRootAttribute() {
        ModelNode op = Util.createEmptyOperation(READ_ATTRIBUTE_OPERATION, PathAddress.pathAddress(HOST_WILD, SERVER_WILD));
        op.get(NAME).set("server-state");
        ModelNode resp = executeForResult(op);
        assertEquals(resp.toString(), expectUnstartedServerResource() ? 4 : 3, resp.asInt());
        int primaryCount = 0;
        for (ModelNode item : resp.asList()) {
            if (isPrimaryItem(item, 2)) {
                primaryCount++;
            }
            assertTrue(item.toString(), VALID_STATES.contains(item.get(RESULT).asString().toLowerCase(Locale.ENGLISH)));
        }
        assertEquals(resp.toString(), 2, primaryCount);

    }

    @Test
    public void testSecondaryAllServersReadRootAttribute() {
        ModelNode op = Util.createEmptyOperation(READ_ATTRIBUTE_OPERATION, PathAddress.pathAddress(HOST_SECONDARY, SERVER_WILD));
        op.get(NAME).set("server-state");
        ModelNode resp = executeForResult(op);
        assertEquals(resp.toString(), expectUnstartedServerResource() ? 2 : 1, resp.asInt());
        int primaryCount = 0;
        for (ModelNode item : resp.asList()) {
            if (isPrimaryItem(item, 2)) {
                primaryCount++;
            }
            assertTrue(item.toString(), VALID_STATES.contains(item.get(RESULT).asString().toLowerCase(Locale.ENGLISH)));
        }
        assertEquals(resp.toString(), 0, primaryCount);

    }

    @Test
    public void testAllHostsAllServersReadInterfaceDescription() {
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, PathAddress.pathAddress(HOST_WILD, SERVER_WILD, INTERFACE_PUBLIC));
        ModelNode resp = executeForResult(op);
        assertEquals(resp.toString(), 2, resp.asInt());
        int primaryCount = 0;
        for (ModelNode item : resp.asList()) {
            if (isPrimaryItem(item, 3)) {
                primaryCount++;
            }
            assertTrue(item.toString(), item.hasDefined(RESULT, ATTRIBUTES, INET_ADDRESS));
        }
        assertEquals(resp.toString(), 1, primaryCount);

    }

    @Test
    public void testSecondaryAllServersReadInterfaceDescription() {
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, PathAddress.pathAddress(HOST_SECONDARY, SERVER_WILD, INTERFACE_PUBLIC));
        ModelNode resp = executeForResult(op);
        assertEquals(resp.toString(), 1, resp.asInt());
        int primaryCount = 0;
        for (ModelNode item : resp.asList()) {
            if (isPrimaryItem(item, 3)) {
                primaryCount++;
            }
            assertTrue(item.toString(), item.hasDefined(RESULT, ATTRIBUTES, INET_ADDRESS));
        }
        assertEquals(resp.toString(), 0, primaryCount);

    }

    @Test
    public void testAllHostsAllServersReadRootDescription() {
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, PathAddress.pathAddress(HOST_WILD, SERVER_WILD));
        ModelNode resp = executeForResult(op);
        assertEquals(resp.toString(), expectUnstartedServerResource() ? 4 : 3, resp.asInt());
        int primaryCount = 0;
        for (ModelNode item : resp.asList()) {
            if (isPrimaryItem(item, 2)) {
                primaryCount++;
            }
            assertTrue(item.toString(), item.hasDefined(RESULT, ATTRIBUTES, "server-state"));
        }
        assertEquals(resp.toString(), 2, primaryCount);

    }

    @Test
    public void testSecondaryAllServersReadRootDescription() {
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_DESCRIPTION_OPERATION, PathAddress.pathAddress(HOST_SECONDARY, SERVER_WILD));
        ModelNode resp = executeForResult(op);
        assertEquals(resp.toString(), expectUnstartedServerResource() ? 2 : 1, resp.asInt());
        int primaryCount = 0;
        for (ModelNode item : resp.asList()) {
            if (isPrimaryItem(item, 2)) {
                primaryCount++;
            }
            assertTrue(item.toString(), item.hasDefined(RESULT, ATTRIBUTES, "server-state"));
        }
        assertEquals(resp.toString(), 0, primaryCount);

    }

    @Test
    public void testWildcardHostRootQuery() {
        // Basic /host=*:query
        ModelNode op = Util.createEmptyOperation(QUERY, PathAddress.pathAddress(HOST_WILD));
        ModelNode resp = executeForResult(op);

        assertEquals(resp.toString(), 2, resp.asInt());
        ModelNode secondaryResult = null;
        int primaryCount = 0;
        for (ModelNode item : resp.asList()) {
            if (isPrimaryItem(item, 1)) {
                primaryCount++;
            } else {
                secondaryResult = item.get(RESULT);
            }
            assertTrue(item.toString(), item.hasDefined(RESULT, "host-state"));
        }
        assertEquals(resp.toString(), 1, primaryCount);
        assertNotNull(resp.toString(), secondaryResult);

        // Now limit the result to secondary hosts
        String dcCheck = version.getMajor() <= 7 ? "master" : "primary";
        op = Util.createEmptyOperation(QUERY, PathAddress.pathAddress(HOST_WILD));
        op.get(WHERE, dcCheck).set(false);
        resp = executeForResult(op);
        assertEquals(resp.toString(), 1, resp.asInt());
        assertEquals(resp.toString(), secondaryResult, resp.get(0).get(RESULT));

        // Now slim down the output
        op.get(SELECT).add(NAME);
        resp = executeForResult(op);
        assertEquals(resp.toString(), 1, resp.asInt());
        assertEquals(resp.toString(), 1, resp.get(0).get(RESULT).keys().size());
        assertEquals(resp.toString(), "secondary", resp.get(0).get(RESULT, NAME).asString());
    }

    @Test
    public void testSpecificHostRootQuery() {
        // /host=secondary:query
        ModelNode op = Util.createEmptyOperation(QUERY, PathAddress.pathAddress(HOST_SECONDARY));
        ModelNode result = executeForResult(op, ModelType.OBJECT);

        assertTrue(result.toString(), result.hasDefined("host-state"));
        assertEquals(result.toString(), "secondary", result.get(NAME).asString());

        String dcCheck = version.getMajor() <= 7 ? "master" : "primary";
        // Now cause the filter to exclude the secondary
        op.get(WHERE, dcCheck).set(true);
        executeForResult(op, ModelType.UNDEFINED);

        // Correct the filter, slim down the input
        op.get(WHERE, dcCheck).set(false);
        op.get(SELECT).add(NAME);
        result = executeForResult(op, ModelType.OBJECT);


        assertEquals(result.toString(), 1, result.keys().size());
        assertEquals(result.toString(), "secondary", result.get(NAME).asString());
    }

    @Test
    public void testWildcardHostServerConfigQuery() {
        // Basic /host=*/server-config=*:query
        ModelNode op = Util.createEmptyOperation(QUERY, PathAddress.pathAddress(HOST_WILD, SERVER_CONFIG_WILD));
        ModelNode resp = executeForResult(op);

        assertEquals(resp.toString(), 4, resp.asInt());
        Set<ModelNode> autoStarts = new HashSet<>();
        int primaryCount = 0;
        for (ModelNode item : resp.asList()) {
            if (isPrimaryItem(item, 2)) {
                primaryCount++;
            }
            ModelNode result = item.get(RESULT);
            assertTrue(item.toString(), result.has(AUTO_START));
            if (result.hasDefined(AUTO_START) && !result.get(AUTO_START).asBoolean()) {
                autoStarts.add(result);
            }
            assertTrue(item.toString(), result.hasDefined(GROUP));
            assertTrue(item.toString(), result.hasDefined("status"));
        }
        assertEquals(resp.toString(), 2, primaryCount);

        // Now limit the result to non-auto-start
        op.get(WHERE, AUTO_START).set(false);
        resp = executeForResult(op);
        assertEquals(resp.toString(), 3, resp.asInt());
        for (ModelNode item : resp.asList()) {
            assertTrue(resp.toString(), autoStarts.contains(item.get(RESULT)));
        }

        // Now slim down the output
        op.get(SELECT).add(GROUP);
        resp = executeForResult(op);
        assertEquals(resp.toString(), 3, resp.asInt());
        for (ModelNode item : resp.asList()) {
            ModelNode result = item.get(RESULT);
            assertEquals(resp.toString(), 1, result.keys().size());
            assertEquals(resp.toString(), "other-server-group", result.get(GROUP).asString());
        }
    }

    @Test
    public void testSpecificHostServerConfigQuery() {
        // Basic /host=secondary/server-config=*:query
        ModelNode op = Util.createEmptyOperation(QUERY, PathAddress.pathAddress(HOST_SECONDARY, SERVER_CONFIG_WILD));
        ModelNode resp = executeForResult(op);

        assertEquals(resp.toString(), 2, resp.asInt());
        Set<ModelNode> autoStarts = new HashSet<>();
        for (ModelNode item : resp.asList()) {
            assertFalse(resp.toString(), isPrimaryItem(item, 2));
            ModelNode result = item.get(RESULT);
            assertTrue(item.toString(), result.has(AUTO_START));
            if (result.hasDefined(AUTO_START) && !result.get(AUTO_START).asBoolean()) {
                autoStarts.add(result);
            }
            assertTrue(item.toString(), result.hasDefined(GROUP));
            assertTrue(item.toString(), result.hasDefined("status"));
        }

        // Now limit the result to auto-start=false servers
        op.get(WHERE, AUTO_START).set(false);
        resp = executeForResult(op);
        assertEquals(resp.toString(), 1, resp.asInt());
        for (ModelNode item : resp.asList()) {
            assertTrue(resp.toString(), autoStarts.contains(item.get(RESULT)));
        }

        // Now slim down the output
        op.get(SELECT).add(GROUP);
        resp = executeForResult(op);
        assertEquals(resp.toString(), 1, resp.asInt());
        for (ModelNode item : resp.asList()) {
            ModelNode result = item.get(RESULT);
            assertEquals(resp.toString(), 1, result.keys().size());
            assertEquals(resp.toString(), "other-server-group", result.get(GROUP).asString());
        }

    }

    @Test
    public void testWildcardServerRootQuery() {
        // Basic /host=*/server=*:query
        ModelNode op = Util.createEmptyOperation(QUERY, PathAddress.pathAddress(HOST_WILD, SERVER_WILD));
        ModelNode resp = executeForResult(op);

        assertEquals(resp.toString(), expectUnstartedServerResource() ? 4 : 3, resp.asInt());
        Set<ModelNode> running = new HashSet<>();
        int primaryCount = 0;
        for (ModelNode item : resp.asList()) {
            String expectedHost;
            if (isPrimaryItem(item, 2)) {
                primaryCount++;
                expectedHost = "primary";
            } else {
                expectedHost = "secondary";
            }
            ModelNode result = item.get(RESULT);
            assertTrue(item.toString(), result.hasDefined("server-state"));
            if (result.get("server-state").asString().toLowerCase(Locale.ENGLISH).equals("running")) {
                assertEquals(resp.toString(), expectedHost, result.get(HOST).asString());
                running.add(result);
            }
        }
        assertEquals(resp.toString(), 2, primaryCount);
        assertEquals(resp.toString(), 2, running.size());

        // Now limit the result to running servers
        op.get(WHERE, "server-state").set("running");
        resp = executeForResult(op);
        assertEquals(resp.toString(), 2, resp.asInt());
        for (ModelNode item : resp.asList()) {
            assertTrue(resp.toString(), running.contains(item.get(RESULT)));
        }

        // Now slim down the output
        op.get(SELECT).add(SERVER_GROUP);
        resp = executeForResult(op);
        assertEquals(resp.toString(), 2, resp.asInt());
        for (ModelNode item : resp.asList()) {
            ModelNode result = item.get(RESULT);
            assertEquals(resp.toString(), 1, result.keys().size());
            assertEquals(resp.toString(), "other-server-group", result.get(SERVER_GROUP).asString());
        }

    }

    @Test
    public void testSpecificHostServerRootQuery() {
        // Basic /host=secondary/server=*:query
        ModelNode op = Util.createEmptyOperation(QUERY, PathAddress.pathAddress(HOST_SECONDARY, SERVER_WILD));
        ModelNode resp = executeForResult(op);

        assertEquals(resp.toString(), expectUnstartedServerResource() ? 2 : 1, resp.asInt());
        Set<ModelNode> running = new HashSet<>();
        for (ModelNode item : resp.asList()) {
            assertFalse(resp.toString(), isPrimaryItem(item, 2));
            ModelNode result = item.get(RESULT);
            assertTrue(item.toString(), result.hasDefined("server-state"));
            if (result.get("server-state").asString().toLowerCase(Locale.ENGLISH).equals("running")) {
                assertEquals(resp.toString(), "secondary", result.get(HOST).asString());
                running.add(result);
            }
        }
        assertEquals(resp.toString(), 1, running.size());

        // Now limit the result to running servers
        op.get(WHERE, "server-state").set("running");
        resp = executeForResult(op);
        assertEquals(resp.toString(), 1, resp.asInt());
        for (ModelNode item : resp.asList()) {
            assertTrue(resp.toString(), running.contains(item.get(RESULT)));
        }

        // Now slim down the output
        op.get(SELECT).add(SERVER_GROUP);
        resp = executeForResult(op);
        assertEquals(resp.toString(), 1, resp.asInt());
        for (ModelNode item : resp.asList()) {
            ModelNode result = item.get(RESULT);
            assertEquals(resp.toString(), 1, result.keys().size());
            assertEquals(resp.toString(), "other-server-group", result.get(SERVER_GROUP).asString());
        }

    }

    @Test
    public void testSpecificServerRootQuery() {
        // /host=secondary/server=server-one:query
        ModelNode op = Util.createEmptyOperation(QUERY, PathAddress.pathAddress(HOST_SECONDARY, SERVER_ONE));
        ModelNode result = executeForResult(op, ModelType.OBJECT);

        assertEquals(result.toString(), "running", result.get("server-state").asString());
        assertEquals(result.toString(), "secondary", result.get(HOST).asString());

        // Now cause the filter to exclude the server
        op.get(WHERE, HOST).set("primary");
        executeForResult(op, ModelType.UNDEFINED);

        // Correct the filter, slim down the input
        op.get(WHERE, HOST).set("secondary");
        op.get(SELECT).add(NAME);
        result = executeForResult(op, ModelType.OBJECT);

        assertEquals(result.toString(), 1, result.keys().size());
        assertEquals(result.toString(), "server-one", result.get(NAME).asString());

    }

    @Test
    public void testWildcardServerWildcardInterfaceQuery() {
        // Basic /host=*/server=*/interface=*:query
        ModelNode op = Util.createEmptyOperation(QUERY, PathAddress.pathAddress(HOST_WILD, SERVER_WILD, INTERFACE_WILD));
        ModelNode resp = executeForResult(op);

        assertEquals(resp.toString(), 2 * 3, resp.asInt());
        int primaryCount = 0;
        for (ModelNode item : resp.asList()) {
            ValueExpression expectedAddress;
            if (isPrimaryItem(item, 3)) {
                primaryCount++;
                expectedAddress = PRIMARY_ADDRESS;
            } else {
                expectedAddress = SECONDARY_ADDRESS;
            }
            ModelNode result = item.get(RESULT);
            assertEquals(resp.toString(), expectedAddress, result.get(INET_ADDRESS).asExpression());
        }
        assertEquals(resp.toString(), 3, primaryCount);

        // Now limit the result to secondary servers
        op.get(WHERE, INET_ADDRESS).set(SECONDARY_ADDRESS);
        resp = executeForResult(op);
        assertEquals(resp.toString(), 3, resp.asInt());

        // Now slim down the output
        op.get(SELECT).add(INET_ADDRESS);
        resp = executeForResult(op);
        assertEquals(resp.toString(), 3, resp.asInt());
        for (ModelNode item : resp.asList()) {
            ModelNode result = item.get(RESULT);
            assertEquals(resp.toString(), 1, result.keys().size());
            assertEquals(resp.toString(), SECONDARY_ADDRESS, result.get(INET_ADDRESS).asExpression());
        }

    }

    @Test
    public void testSpecificServerWildcardInterfaceQuery() {
        // Basic /host=secondary/server=server-one/interface=*:query
        ModelNode op = Util.createEmptyOperation(QUERY, PathAddress.pathAddress(HOST_SECONDARY, SERVER_ONE, INTERFACE_WILD));
        ModelNode resp = executeForResult(op);

        assertEquals(resp.toString(), 3, resp.asInt());
        for (ModelNode item : resp.asList()) {
            ModelNode result = item.get(RESULT);
            assertEquals(resp.toString(), SECONDARY_ADDRESS, result.get(INET_ADDRESS).asExpression());
        }

        // Now limit the result to primary servers
        // This is a wildcard request, so the result should be an empty list
        op.get(WHERE, INET_ADDRESS).set(PRIMARY_ADDRESS);
        resp = executeForResult(op);
        assertEquals(resp.toString(), 0, resp.asInt());

        // Now correct the filter and slim down the output
        op.get(WHERE, INET_ADDRESS).set(SECONDARY_ADDRESS);
        op.get(SELECT).add(INET_ADDRESS);
        resp = executeForResult(op);
        assertEquals(resp.toString(), 3, resp.asInt());
        for (ModelNode item : resp.asList()) {
            ModelNode result = item.get(RESULT);
            assertEquals(resp.toString(), 1, result.keys().size());
            assertEquals(resp.toString(), SECONDARY_ADDRESS, result.get(INET_ADDRESS).asExpression());
        }

    }

    @Test
    public void testSpecificServerSpecificSocketBindingQuery() {
        // /host=secondary/server=server-one/socket-binding-group=standard-sockets/socket-binding=*:query
        ModelNode op = Util.createEmptyOperation(QUERY, PathAddress.pathAddress(HOST_SECONDARY, SERVER_ONE).append(SOCKET_BINDING_HTTP));
        ModelNode result = executeForResult(op, ModelType.OBJECT);

        assertEquals(result.toString(), 8080, result.get(PORT).asInt());
        assertFalse(result.toString(), result.hasDefined(INTERFACE));

        // Now cause the filter to exclude the server
        op.get(WHERE, INTERFACE).set("bogus");
        executeForResult(op, ModelType.UNDEFINED);

        // Correct the filter, slim down the input
        op.get(WHERE, INTERFACE).set("undefined");
        op.get(SELECT).add(PORT);
        result = executeForResult(op, ModelType.OBJECT);

        assertEquals(result.toString(), 1, result.keys().size());
        assertEquals(result.toString(), 8080, result.get(PORT).asInt());

    }

    protected boolean expectUnstartedServerResource() {
        return true;
    }

    private ModelNode executeForResult(ModelNode op) {
        return executeForResult(op, ModelType.LIST);
    }

    private static ModelNode executeForResult(ModelNode op, ModelType expectedType) {
        try {
            ModelNode response = support.getDomainPrimaryLifecycleUtil().getDomainClient().execute(op);
            assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
            ModelNode result = response.get(RESULT);
            assertEquals(result.toString(), expectedType, result.getType());
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isPrimaryItem(ModelNode item, int itemSize) {
        assertTrue(item.toString(), item.hasDefined(ADDRESS));
        PathAddress pa = PathAddress.pathAddress(item.get(ADDRESS));
        assertEquals(item.toString(), itemSize, pa.size());
        return pa.getElement(0).getValue().equals("primary");
    }
}
