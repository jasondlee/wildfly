/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.jca;

import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

import org.jboss.as.connector.services.workmanager.StatisticsExecutorImpl;
import org.jboss.as.connector.subsystems.datasources.WildFlyDataSource;
import org.jboss.jca.adapters.jdbc.WrapperDataSource;
import org.jboss.jca.core.api.connectionmanager.pool.PoolConfiguration;
import org.jboss.jca.core.connectionmanager.ConnectionManager;
import org.jboss.jca.core.connectionmanager.pool.api.Pool;
import org.jboss.jca.core.connectionmanager.pool.mcp.ManagedConnectionPool;
import org.jboss.jca.core.util.Injection;

/**
 * Utility class for Jakarta Connectors integration test
 *
 * @author <a href="mailto:msimka@redhat.com">Martin Simka</a>
 */
public class JcaTestsUtil {

    /**
     * set lastIdleCheck property in ManagedConnectionPool to minimun and ManagedConnectionPool's removeIdleConnection
     *
     * @param mcp
     * @throws Exception
     */
    public static void callRemoveIdleConnections(ManagedConnectionPool mcp) throws Exception {
        Injection in = new Injection();
        in.inject(mcp, "lastIdleCheck", Long.MIN_VALUE, long.class.getName(), true);
        mcp.removeIdleConnections();
    }

    /**
     * Extract ManagedConnectionPool from ConnectionFactory by using reflection
     *
     * @param connectionFactory
     * @return ManagedConnectionPool instance. <code>null</code> if not found
     */
    public static ManagedConnectionPool extractManagedConnectionPool(Object connectionFactory) {
        ConnectionManager cm = extractConnectionManager(connectionFactory);

        // org.jboss.jca.core.connectionmanager.pool.strategy.OnePool
        Object onePool = cm.getPool();
        Class<?> clz = onePool.getClass();
        // org.jboss.jca.core.connectionmanager.pool.AbstractPrefillPool
        clz = clz.getSuperclass();
        // org.jboss.jca.core.connectionmanager.pool.AbstractPool
        clz = clz.getSuperclass();

        try {
            Method getManagedConnectionPools = clz.getDeclaredMethod("getManagedConnectionPools");
            getManagedConnectionPools.setAccessible(true);
            ConcurrentMap<Object, ManagedConnectionPool> mcps = (ConcurrentMap<Object, ManagedConnectionPool>) getManagedConnectionPools.invoke(onePool);
            return mcps.values().iterator().next();
        } catch (Throwable t) {
            fail(t.getMessage());
        }
        return null;
    }

    public static PoolConfiguration exctractPoolConfiguration(Object connectionFactory) {
        ConnectionManager cm = extractConnectionManager(connectionFactory);

        // org.jboss.jca.core.connectionmanager.pool.strategy.OnePool
        Pool pool = cm.getPool();
        Class<?> clz = pool.getClass();
        // org.jboss.jca.core.connectionmanager.pool.AbstractPrefillPool
        clz = clz.getSuperclass();
        // org.jboss.jca.core.connectionmanager.pool.AbstractPool
        clz = clz.getSuperclass();

        try {
            Method getPoolConfiguration = clz.getDeclaredMethod("getPoolConfiguration");
            getPoolConfiguration.setAccessible(true);

            return (PoolConfiguration) getPoolConfiguration.invoke(pool);
        } catch (Throwable t) {
            fail(t.getMessage());
        }
        return null;
    }

    public static PoolConfiguration extractDSPoolConfiguration(Object datasource) {
        try {
            Field delegateField = datasource.getClass().getDeclaredField("delegate");
            Object delegate = delegateField.get(datasource);
        } catch (Throwable t) {
            fail(t.getMessage());
        }
        return null;
    }

    /**
     * Extract ConnectionManager from the passed object
     *
     * @param connectionFactory The object; typically a ConnectionFactory implementation
     * @return The connection manager; <code>null</code> if not found
     */
    private static ConnectionManager extractConnectionManager(Object connectionFactory) {
        Class<?> clz = connectionFactory.getClass();

        while (!Object.class.equals(clz)) {
            try {
                Field[] fields = clz.getDeclaredFields();

                if (fields != null && fields.length > 0) {
                    for (Field field : fields) {
                        Class<?> fieldType = field.getType();
                        if (fieldType.equals(jakarta.resource.spi.ConnectionManager.class) ||
                                fieldType.equals(ConnectionManager.class)) {
                            field.setAccessible(true);
                            return (ConnectionManager) field.get(connectionFactory);
                        }
                    }
                }
            } catch (Throwable t) {
                fail(t.getMessage());
            }
            try {
                Method[] methods = clz.getDeclaredMethods();
                for (Method method : methods) {
                    Class<?> type = method.getReturnType();
                    if (type.equals(jakarta.resource.spi.ConnectionManager.class) ||
                            type.equals(ConnectionManager.class)) {
                        method.setAccessible(true);
                        return (ConnectionManager) method.invoke(connectionFactory);
                    }
                }
            } catch (Throwable t) {
                fail(t.getMessage());
            }
            clz = clz.getSuperclass();
        }
        return null;
    }

    /**
     * Extract WrapperDataSource from WildflyDataSource by using reflection
     *
     * @param wfds
     * @return WrapperDataSource instance, <code>null</code> if not found
     */
    public static WrapperDataSource extractWrapperDatasource(WildFlyDataSource wfds) {
        Class<?> clazz = wfds.getClass();
        try {
            Field delegate = clazz.getDeclaredField("delegate");
            delegate.setAccessible(true);
            return (WrapperDataSource) delegate.get(wfds);
        } catch (Throwable t) {
            fail(t.getMessage());
        }
        return null;
    }

    /**
     * Extract the backing, "real" executor from StatisticsExecutorImpl by using reflection
     *
     * @param sei the {@code StatisticsExecutorImpl}
     * @return {@code Executor} instance, <code>null</code> if not found
     */
    public static Executor extractRealExecutor(StatisticsExecutorImpl sei) {
        Class<?> clazz = sei.getClass();
        try {
            Field delegate = clazz.getDeclaredField("realExecutor");
            delegate.setAccessible(true);
            return (Executor) delegate.get(sei);
        } catch (Throwable t) {
            fail(t.getMessage());
        }
        return null;
    }
}
