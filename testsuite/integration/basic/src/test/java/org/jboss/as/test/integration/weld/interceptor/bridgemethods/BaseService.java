/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.weld.interceptor.bridgemethods;

/**
 *
 */
public interface BaseService<T> {
    void doSomething(T param);
}