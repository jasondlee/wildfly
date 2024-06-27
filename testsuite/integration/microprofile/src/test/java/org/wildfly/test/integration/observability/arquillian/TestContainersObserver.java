/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.integration.observability.arquillian;

import java.util.concurrent.atomic.AtomicReference;

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
    protected InstanceProducer<AtomicReference<GenericContainer<?>>> containerWrapper;

    public void createContainer(@Observes BeforeClass beforeClass) {
        TestClass javaClass = beforeClass.getTestClass();
        TestContainer tcAnno = javaClass.getAnnotation(TestContainer.class);
        System.err.println("***** " + "Test class = " + javaClass.getJavaClass());
        System.err.println("***** " + "  tcAnno = " + tcAnno);
        if (tcAnno != null) {
            Class<? extends GenericContainer<?>> clazz = tcAnno.value();
            try {
                containerWrapper.set(new AtomicReference<>(clazz.getDeclaredConstructor().newInstance()));
            } catch (Exception e) { //Clean up
                throw new RuntimeException(e);
            }
        }
    }

    public void stopContainer(@Observes AfterClass afterClass) {
        System.err.println("***** " + "  After test class = " + afterClass.getTestClass().getJavaClass());
        AtomicReference<GenericContainer<?>> reference = containerWrapper.get();
        if (reference != null) {
            GenericContainer<?> container = reference.get();
            if (container != null) {
                container.stop();
            }
            containerWrapper.set(new AtomicReference<>()); // Clear out old container
        }
    }
}
