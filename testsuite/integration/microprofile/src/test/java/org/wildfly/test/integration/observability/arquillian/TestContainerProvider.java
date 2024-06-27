/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.integration.observability.arquillian;

import java.lang.annotation.Annotation;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.container.AbstractTargetsContainerProvider;
import org.testcontainers.containers.GenericContainer;

public class TestContainerProvider extends AbstractTargetsContainerProvider {
    @Inject
    private Instance<AtomicReference<GenericContainer<?>>> genericContainerInstance;

    @Override
    public Object doLookup(ArquillianResource resource, Annotation... qualifiers) {
        AtomicReference<GenericContainer<?>> reference = genericContainerInstance.get();
        if (reference != null) {
            GenericContainer<?> container = reference.get();
            if (container != null && !container.isRunning()) {
                container.start();
            }
            return container;
        } else {
            return null;
        }
    }

    @Override
    public boolean canProvide(Class<?> type) {
        return GenericContainer.class.isAssignableFrom(type);
    }
}
