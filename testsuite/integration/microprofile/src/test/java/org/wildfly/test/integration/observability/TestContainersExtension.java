/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.integration.observability;

import org.jboss.arquillian.core.spi.LoadableExtension;
import org.jboss.arquillian.test.spi.TestEnricher;

public class TestContainersExtension implements LoadableExtension {
    @Override
    public void register(ExtensionBuilder builder) {
        builder.service(TestEnricher.class, TestContainersEnricher.class);
    }
}
