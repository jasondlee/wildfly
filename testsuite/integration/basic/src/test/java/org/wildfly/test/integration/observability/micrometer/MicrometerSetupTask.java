package org.wildfly.test.integration.observability.micrometer;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.ServerReload;
import org.jboss.dmr.ModelNode;

public class MicrometerSetupTask implements ServerSetupTask {
    private final String WILDFLY_EXTENSION_MICROMETER = "org.wildfly.extension.micrometer";
    private final ModelNode subsystemAddress = Operations.createAddress("subsystem", "micrometer");
    private final ModelNode extensionAddress = Operations.createAddress("extension", WILDFLY_EXTENSION_MICROMETER);

    @Override
    public void setup(ManagementClient managementClient, String s) throws Exception {
        execute(managementClient, Operations.createAddOperation(extensionAddress), true);
        execute(managementClient, Operations.createAddOperation(subsystemAddress), true);

        ServerReload.reloadIfRequired(managementClient);
    }

    @Override
    public void tearDown(ManagementClient managementClient, String s) throws Exception {
        execute(managementClient, Operations.createRemoveOperation(subsystemAddress), true);
        execute(managementClient, Operations.createRemoveOperation(extensionAddress), true);
        ServerReload.reloadIfRequired(managementClient);
    }

    private ModelNode execute(final org.jboss.as.arquillian.container.ManagementClient managementClient,
                              final ModelNode op,
                              final boolean expectSuccess) throws IOException {
        ModelNode response = managementClient.getControllerClient().execute(op);
        final String outcome = response.get("outcome").asString();
        if (expectSuccess) {
            assertEquals(response.toString(), "success", outcome);
            return response.get("result");
        } else {
            assertEquals("failed", outcome);
            return response.get("failure-description");
        }
    }
}
