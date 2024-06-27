/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.integration.observability.arquillian;

import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.ApplicationScoped;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.arquillian.test.spi.event.suite.AfterClass;
import org.jboss.arquillian.test.spi.event.suite.BeforeClass;
import org.testcontainers.containers.GenericContainer;

public class TestContainersObserver {
    @Inject
    @ApplicationScoped
    private InstanceProducer<GenericContainer<?>> containerWrapper;

    public void createAndStartContainer(@Observes BeforeClass beforeClass) {
        TestClass javaClass = beforeClass.getTestClass();
        TestContainer tcAnno = javaClass.getAnnotation(TestContainer.class);
        if (tcAnno != null) {
            Class<? extends GenericContainer<?>> clazz = tcAnno.value();
            try {
                GenericContainer<?> instance = clazz.getDeclaredConstructor().newInstance();
                instance.start();
                containerWrapper.set(instance);
            } catch (Exception e) { //Clean up
                throw new RuntimeException(e);
            }
        }
    }

    public void stopContainer(@Observes AfterClass afterClass) {
        GenericContainer<?> container = containerWrapper.get();
        if (container != null) {
            container.stop();
        }
    }
}
