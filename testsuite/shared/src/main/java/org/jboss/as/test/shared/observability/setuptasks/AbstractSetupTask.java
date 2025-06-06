/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.shared.observability.setuptasks;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.io.IOException;

import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;

public abstract class AbstractSetupTask implements ServerSetupTask {
    public ModelNode clearAttribute(String address, String attributeName) {
        ModelNode op = Operations.createOperation("write-attribute", Operations.createAddress(SUBSYSTEM, address));
        op.get("name").set(attributeName);
        return op;
    }

    public ModelNode writeAttribute(String subsystem, String name, String value) {
        return writeAttribute(Operations.createAddress(SUBSYSTEM, subsystem), name, value);
    }

    public ModelNode writeAttribute(ModelNode address, String name, String value) {
        return Operations.createWriteAttributeOperation(address, name, value);
    }

    public void executeOp(final ManagementClient client, final ModelNode op) throws IOException {
        executeOp(client.getControllerClient(), Operation.Factory.create(op));
    }

    public void executeOp(final ModelControllerClient client, final Operation op) throws IOException {
        final ModelNode result = client.execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            throw new RuntimeException("Failed to execute operation: " + Operations.getFailureDescription(result)
                    .asString());
        }
    }

    public ModelNode executeRead(final ManagementClient managementClient, ModelNode address) throws IOException {
        return managementClient.getControllerClient().execute(Operations.createReadResourceOperation(address));
    }
}
