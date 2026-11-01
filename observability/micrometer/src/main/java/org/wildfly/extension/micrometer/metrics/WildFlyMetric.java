/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.micrometer.metrics;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLES;
import static org.wildfly.extension.micrometer.MicrometerExtensionLogger.MICROMETER_LOGGER;

import java.util.OptionalDouble;

import org.jboss.as.controller.LocalModelControllerClient;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.micrometer.registry.WildFlyRegistry;

public class WildFlyMetric implements Metric {

    private static final ModelNode UNDEFINED = new ModelNode();

    private PathAddress address;
    private String attributeName;

    static {
        UNDEFINED.protect();
    }

    public WildFlyMetric() {

    }

    public WildFlyMetric(PathAddress address, String attributeName) {
        this.address = address;
        this.attributeName = attributeName;
    }

    public PathAddress getAddress() {
        return address;
    }

    /** Reads the metric with the client scoped to the current exporter, or returns empty outside an export. */
    public OptionalDouble getValue() {
        LocalModelControllerClient client = WildFlyRegistry.getCurrentModelControllerClient();
        if (client == null) {
            return OptionalDouble.empty();
        }
        ModelNode result = readAttributeValue(client, address, attributeName);
        if (result.isDefined()) {
            try {
                return OptionalDouble.of(result.asDouble());
            } catch (Exception e) {
                MICROMETER_LOGGER.unableToConvertAttribute(attributeName, address, e);
            }
        }
        return OptionalDouble.empty();
    }

    /** Reads the metric using the client supplied by the registry callback. */
    @Override
    public OptionalDouble getValue(LocalModelControllerClient mcc) {
        if (mcc == null || mcc == WildFlyRegistry.getCurrentModelControllerClient()) {
            return getValue();
        }
        return WildFlyRegistry.withModelControllerClient(mcc, this::getValue);
    }

    private ModelNode readAttributeValue(LocalModelControllerClient mcc, PathAddress address, String attributeName) {
        final ModelNode readAttributeOp = new ModelNode();
        readAttributeOp.get(OP).set(READ_ATTRIBUTE_OPERATION);
        readAttributeOp.get(OP_ADDR).set(address.toModelNode());
        readAttributeOp.get(ModelDescriptionConstants.INCLUDE_UNDEFINED_METRIC_VALUES).set(false);
        readAttributeOp.get(NAME).set(attributeName);
        readAttributeOp.get(OPERATION_HEADERS).get(ROLES).add("Monitor");

        ModelNode response = mcc.execute(readAttributeOp);
        String error = getFailureDescription(response);
        if (error != null) {
            // [WFLY-11933] if the value can not be read if the management resource is not accessible due to RBAC,
            // it is logged it at a lower level.
            if (error.contains("WFLYCTL0216")) {
                MICROMETER_LOGGER.debugf("Unable to read attribute %s: %s.", attributeName, error);
            } else{
                MICROMETER_LOGGER.unableToReadAttribute(attributeName, address, error);
            }
            return UNDEFINED;
        }
        return  response.get(RESULT);
    }

    private String getFailureDescription(ModelNode result) {
        if (result.hasDefined(FAILURE_DESCRIPTION)) {
            return result.get(FAILURE_DESCRIPTION).toString();
        }
        return null;
    }
}
