package org.wildfly.test.integration.observability;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;

import org.jboss.arquillian.test.spi.TestEnricher;
import org.jboss.as.test.shared.util.AssumeTestGroupUtil;
import org.wildfly.test.integration.observability.container.OpenTelemetryCollectorContainer;

public class TestContainersEnricher implements TestEnricher {
    private static final ThreadLocal<OpenTelemetryCollectorContainer> otelCollectorContainer = new ThreadLocal<>();

    @Override
    public void enrich(Object testCase) {
        if (AssumeTestGroupUtil.isDockerAvailable()) {
            for (Field field : getFieldsWithAnnotation(testCase.getClass())) {
                OpenTelemetryCollectorContainer value = lookup(field.getType());
                try {
                    if (!field.canAccess(testCase)) {
                        field.setAccessible(true);
                    }
                    field.set(testCase, value);
                } catch (Exception e) {
                    throw new RuntimeException("Could not set value on field " + field + " using " + value, e);
                }
            }
        }
    }

    private OpenTelemetryCollectorContainer lookup(Class<?> type) {
        if (type.isAssignableFrom(OpenTelemetryCollectorContainer.class)) {

            OpenTelemetryCollectorContainer container = otelCollectorContainer.get();
            if (container == null) {
                container = new OpenTelemetryCollectorContainer();
                container.start();
                otelCollectorContainer.set(container);
            }
            return container;
        }
        return null;
    }

    @Override
    public Object[] resolve(Method method) {
        return new Object[0];
    }

    private List<Field> getFieldsWithAnnotation(final Class<?> source) {
        return AccessController.doPrivileged((PrivilegedAction<List<Field>>) () -> {
            List<Field> foundFields = new ArrayList<>();
            Class<?> nextSource = source;
            while (nextSource != Object.class) {
                for (Field field : nextSource.getDeclaredFields()) {
                    if (field.isAnnotationPresent(TestContainer.class)) {
                        field.setAccessible(true);
                        foundFields.add(field);
                    }
                }
                nextSource = nextSource.getSuperclass();
            }
            return foundFields;
        });
    }
}
