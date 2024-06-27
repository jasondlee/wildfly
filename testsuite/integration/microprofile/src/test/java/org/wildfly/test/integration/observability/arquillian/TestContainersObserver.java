/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.integration.observability.arquillian;

import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.arquillian.test.spi.annotation.ClassScoped;
import org.jboss.arquillian.test.spi.event.suite.AfterClass;
import org.jboss.arquillian.test.spi.event.suite.BeforeClass;
import org.testcontainers.containers.GenericContainer;

public class TestContainersObserver {
    @Inject
    @ClassScoped
    protected InstanceProducer<GenericContainer<?>> containerWrapper;

    public void createContainer(@Observes(precedence = 500) BeforeClass beforeClass) {
        TestClass javaClass = beforeClass.getTestClass();
        TestContainer tcAnno = javaClass.getAnnotation(TestContainer.class);
        System.err.println("***** " + "Test class = " + javaClass.getJavaClass());
        System.err.println("***** " + "  tcAnno = " + tcAnno);
        if (tcAnno != null) {
            Class<? extends GenericContainer<?>> clazz = tcAnno.value();
            try {
                final GenericContainer<?> container = clazz.getConstructor().newInstance();
                container.start();
                containerWrapper.set(container);
            } catch (Exception e) { //Clean up
                throw new RuntimeException(e);
            }
        }
    }

    public void stopContainer(@Observes AfterClass afterClass) {
        System.err.println("***** " + "  After test class = " + afterClass.getTestClass().getJavaClass());
        GenericContainer<?> container = containerWrapper.get();
        if (container != null) {
            container.stop();
        }
    }
}
