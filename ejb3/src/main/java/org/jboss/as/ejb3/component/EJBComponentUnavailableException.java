/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.component;

import jakarta.ejb.EJBException;

/**
 * An exception which can be used to indicate that a particular Jakarta Enterprise Beans component is (no longer) available for handling invocations.
 * This typically is thrown when a Jakarta Enterprise Beans bean is invoked
 * after the Jakarta Enterprise Beans component has been marked for shutdown.
 *
 * @author Jaikiran Pai
 */
public class EJBComponentUnavailableException extends EJBException {

    public EJBComponentUnavailableException() {

    }

    public EJBComponentUnavailableException(final String msg) {
        super(msg);
    }

    public EJBComponentUnavailableException(final String msg, final Exception e) {
        super(msg, e);
    }

    public EJBComponentUnavailableException(final Exception e) {
        super(e);
    }
}
