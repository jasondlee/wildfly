/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.weld.deployment.processors;

import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ejb3.component.session.SessionBeanComponentDescription;
import org.jboss.as.weld.spi.ImplicitBeanArchiveDetector;

/**
 *
 * @author Martin Kouba
 */
public class EjbImplicitBeanArchiveDetector implements ImplicitBeanArchiveDetector {

    @Override
    public boolean isImplicitBeanArchiveRequired(ComponentDescription description) {
        return description instanceof SessionBeanComponentDescription;
    }

}
