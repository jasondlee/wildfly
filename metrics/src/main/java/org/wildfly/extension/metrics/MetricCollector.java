/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.metrics;

import static org.jboss.as.controller.PathAddress.EMPTY_ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.wildfly.extension.metrics.MetricMetadata.Type.COUNTER;
import static org.wildfly.extension.metrics.MetricMetadata.Type.GAUGE;
import static org.wildfly.extension.metrics._private.MetricsLogger.LOGGER;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.LocalModelControllerClient;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessStateNotifier;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

public class MetricCollector {
    private final LocalModelControllerClient modelControllerClient;
    private final ProcessStateNotifier processStateNotifier;
    private final ResourceMetricsRetryQueue resourceMetricsRetryQueue;
    private ImmutableManagementResourceRegistration managementResourceRegistration;
    private MetricRegistration metricRegistration;
    private boolean exposeAnySubsystem;
    private List<String> exposedSubsystems;
    private String prefix;
    private final Map<PathAddress, RuntimeException> lastFailures = new ConcurrentHashMap<>();

    /**
     * Creates a metric collector.
     *
     * @param modelControllerClient The client used for metric discovery
     * @param processStateNotifier  notifier used to defer metric registration until the server
     *                              is running
     * @param executor              the management executor used for asynchronous collection
     * @param exposeAnySubsystem    expose all subsystems
     * @param exposedSubsystems     list of subsystems to expose
     */
    public MetricCollector(LocalModelControllerClient modelControllerClient,
            ProcessStateNotifier processStateNotifier,
            Executor executor,
            boolean exposeAnySubsystem,
            List<String> exposedSubsystems) {
        this.modelControllerClient = modelControllerClient;
        this.processStateNotifier = processStateNotifier;
        this.exposeAnySubsystem = exposeAnySubsystem;
        this.exposedSubsystems = exposedSubsystems;
        this.resourceMetricsRetryQueue = new ResourceMetricsRetryQueue(executor,
            this::processPendingResource,
            this::unableToRegisterMetric);
    }

    /**
     * Queues a newly added management resource for asynchronous metric collection.
     *
     * @param address the address of the added resource
     */
    public synchronized void resourceAdded(PathAddress address) {
        if (managementResourceRegistration == null || metricRegistration == null) {
            return;
        }
        if (isExposingMetrics(address)) {
            resourceMetricsRetryQueue.add(address);
        }
    }

    /**
     * Removes metrics and pending collection work for a removed management resource.
     *
     * @param address the address of the removed resource
     */
    public synchronized void resourceRemoved(PathAddress address) {
        resourceMetricsRetryQueue.remove(address);
        lastFailures.remove(address);
        if (metricRegistration != null) {
            metricRegistration.unregister(address);
        }
    }

    /**
     * Stops asynchronous collection and unregisters all model metrics.
     */
    public synchronized void stop() {
        resourceMetricsRetryQueue.stop();
        lastFailures.clear();
        if (metricRegistration != null) {
            metricRegistration.unregister();
            metricRegistration = null;
        }
        managementResourceRegistration = null;
        exposedSubsystems = null;

        modelControllerClient.close();
    }

    public synchronized void collectRootResourceMetrics(Resource resource,
            ImmutableManagementResourceRegistration imrr,
            String prefix,
            MetricRegistration metricRegistration) {
        managementResourceRegistration = imrr;
        this.metricRegistration = metricRegistration;
        this.prefix = prefix;
        registerResourceMetrics(resource, imrr, Function.identity(), this.prefix, metricRegistration);
    }

    // collect metrics from the resources
    public synchronized void registerResourceMetrics(final Resource resource,
            ImmutableManagementResourceRegistration managementResourceRegistration,
            Function<PathAddress, PathAddress> resourceAddressResolver,
            String prefix,
            MetricRegistration metricRegistration) {
        createRegistrationTasks(resource, managementResourceRegistration, EMPTY_ADDRESS,
                                resourceAddressResolver, metricRegistration, prefix);
        // Defer the actual metric registration until the server is running and they can be
        // collected w/o errors
        PropertyChangeListener listener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (ControlledProcessState.State.RUNNING == evt.getNewValue()) {
                    metricRegistration.register();
                } else if (ControlledProcessState.State.STOPPING == evt.getNewValue()) {
                    // Unregister so if this is a reload they won't still be around in a static cache in MetricsRegistry
                    // and cause problems when the server is starting
                    metricRegistration.unregister();
                    processStateNotifier.removePropertyChangeListener(this);
                }

            }
        };
        this.processStateNotifier.addPropertyChangeListener(listener);
        metricRegistration.addCleanUpTask(() ->  processStateNotifier.removePropertyChangeListener(listener));

        // If server is already running, we won't get a change event so register now
        if (ControlledProcessState.State.RUNNING == this.processStateNotifier.getCurrentState()) {
            metricRegistration.register();
        }
    }

    /**
     * Attempts one read and registration for a queued resource.
     *
     * @param pendingResource the queued resource generation
     * @param attempt the one-based attempt number
     * @return {@code true} when processing is complete, otherwise retry
     */
    private boolean processPendingResource(
            ResourceMetricsRetryQueue.PendingResource pendingResource,
            int attempt) {
        PathAddress address = pendingResource.address();
        ImmutableManagementResourceRegistration imrr;
        MetricRegistration metricRegistration;
        boolean exposeAny;
        List<String> exposed;
        String metricPrefix;
        synchronized (this) {
            imrr = managementResourceRegistration;
            metricRegistration = this.metricRegistration;
            exposeAny = exposeAnySubsystem;
            exposed = exposedSubsystems;
            metricPrefix = prefix;
        }
        if (imrr == null || metricRegistration == null) {
            return true;
        }
        try {
            ModelNode result = modelControllerClient.execute(
                    Operations.createReadResourceOperation(address.toModelNode(), true));
            if (result != null && Operations.isSuccessfulOutcome(result)) {
                Resource resource = Resource.Factory.create();
                resource.writeModel(result.get("result"));
                synchronized (this) {
                    if (System.nanoTime() < pendingResource.deadline()
                            && resourceMetricsRetryQueue.isPending(pendingResource)
                            && managementResourceRegistration == imrr
                            && this.metricRegistration == metricRegistration) {
                        createRegistrationTasks(resource, imrr, address, Function.identity(),
                            metricRegistration, prefix);
                        metricRegistration.register();
                        lastFailures.remove(address);
                        return true;
                    }
                    if (resourceMetricsRetryQueue.isPending(pendingResource)) {
                        lastFailures.remove(address);
                    }
                }
            }
        } catch (RuntimeException e) {
            synchronized (this) {
                if (resourceMetricsRetryQueue.isPending(pendingResource)) {
                    lastFailures.put(address, e);
                }
            }
        }
        return false;
    }

    /**
     * Logs the final failure after an address has exhausted its retry deadline.
     *
     * @param address the resource address that could not be read
     * @param attempts the number of attempts made
     */
    private synchronized void unableToRegisterMetric(PathAddress address, int attempts) {
        RuntimeException lastFailure = lastFailures.remove(address);
        LOGGER.unableToRegisterMetric(address, attempts,
                                      lastFailure == null ? "" : ": " + lastFailure.getMessage());
    }

    private void createRegistrationTasks(final Resource current,
            ImmutableManagementResourceRegistration managementResourceRegistration,
            PathAddress address,
            Function<PathAddress, PathAddress> resourceAddressResolver,
            MetricRegistration registration,
            String prefix) {
        if (!isExposingMetrics(address)) {
            return;
        }

        Map<String, AttributeAccess> attributes = managementResourceRegistration.getAttributes(address);
        if (attributes == null) {
            return;
        }

        ModelNode resourceDescription = null;
        for (Map.Entry<String, AttributeAccess> entry : attributes.entrySet()) {
            String attributeName = entry.getKey();

            final AttributeAccess attributeAccess = entry.getValue();
            if (!isCollectibleMetric(attributeAccess)) {
                continue;
            }

            if (resourceDescription == null) {
                DescriptionProvider modelDescription = managementResourceRegistration.getModelDescription(address);
                resourceDescription = modelDescription.getModelDescription(Locale.getDefault());
            }
            final PathAddress resourceAddress = resourceAddressResolver.apply(address);
            boolean isCounter = attributeAccess.getFlags().contains(AttributeAccess.Flag.COUNTER_METRIC);
            String attributeDescription = resourceDescription.get(ATTRIBUTES, attributeName, DESCRIPTION).asStringOrNull();

            registration.addRegistrationTask(() -> registration.registerMetric(
                    new WildFlyMetric(resourceAddress, attributeName),
                    new WildFlyMetricMetadata(attributeName, resourceAddress, prefix,
                                              attributeDescription,
                                              attributeAccess.getAttributeDefinition().getMeasurementUnit(),
                                              isCounter ? COUNTER : GAUGE)));
        }

        for (String type : current.getChildTypes()) {
            for (Resource.ResourceEntry entry : current.getChildren(type)) {
                createRegistrationTasks(entry, managementResourceRegistration,
                                        address.append(entry.getPathElement()),
                                        resourceAddressResolver, registration, prefix);
            }
        }
    }

    private boolean isExposingMetrics(PathAddress address) {
        // root resource
        if (address.size() == 0) {
            return true;
        }
        String subsystemName = getSubsystemName(address);
        if (subsystemName != null) {
            return exposeAnySubsystem || exposedSubsystems.contains(subsystemName);
        }
        // do not expose metrics for resources outside the subsystems and deployments.
        return false;
    }

    private String getSubsystemName(PathAddress address) {
        if (address.size() == 0) {
            return null;
        }
        if (address.getElement(0).getKey().equals(SUBSYSTEM)) {
            return address.getElement(0).getValue();
        } else {
            return getSubsystemName(address.subAddress(1));
        }
    }

    private boolean isCollectibleMetric(AttributeAccess attributeAccess) {
        if (attributeAccess.getAccessType() == AttributeAccess.AccessType.METRIC
                && attributeAccess.getStorageType() == AttributeAccess.Storage.RUNTIME) {
            // handle only metrics with simple numerical types
            ModelType type = attributeAccess.getAttributeDefinition().getType();
            if (type == ModelType.INT ||
                    type == ModelType.LONG ||
                    type == ModelType.DOUBLE) {
                return true;
            }
        }
        return false;
    }
}
